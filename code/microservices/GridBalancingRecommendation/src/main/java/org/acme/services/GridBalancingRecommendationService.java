package org.acme.services;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.GridBalancingEvaluateRequest;
import org.acme.dto.GridCellDTO;
import org.acme.dto.GridCellEvaluationResult;
import org.acme.dto.SingleCellEvaluationRequest;
import org.acme.dto.TelemetryDTO;
import org.acme.entities.BalancingRecommendation;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Detects overloaded zones and generates load-shift recommendations.
 * Per zone: EV chargers and charging batteries add to demand; solar and discharging batteries add to supply.
 * A zone is overloaded when net load exceeds maxCapacity * thresholdPercent (default 90%).
 * Target is the zone with the most headroom; transferable = min(overload, headroom).
 */
@ApplicationScoped
public class GridBalancingRecommendationService {

    @Inject
    MySQLPool client;

    @Inject
    @Channel("grid-balancing-recommendation")
    Emitter<String> recommendationEmitter;

    @ConfigProperty(name = "gridbalancing.threshold.percent", defaultValue = "0.9")
    double thresholdPercent;

    public List<BalancingRecommendation> evaluateRecommendations(GridBalancingEvaluateRequest request) {
        if (request.sourceCell == null || request.sourceCell.gridCellId == null
                || request.sourceCell.maxCapacity == null) {
            return Collections.emptyList();
        }

        LocalDateTime timestamp = LocalDateTime.now();
        List<TelemetryDTO> telemetry = request.allTelemetry != null ? request.allTelemetry : Collections.emptyList();

        Map<String, List<TelemetryDTO>> byZone = telemetry.stream()
                .filter(t -> t.grid_cell_id != null)
                .collect(Collectors.groupingBy(t -> t.grid_cell_id));

        ZoneMetrics source = new ZoneMetrics(request.sourceCell.gridCellId, request.sourceCell.maxCapacity);
        accumulate(source, byZone.getOrDefault(source.gridCellId, Collections.emptyList()));
        source.compute(thresholdPercent);

        if (source.netLoadKw <= source.thresholdLimitKw) {
            return Collections.emptyList();
        }

        List<ZoneMetrics> neighbours = new ArrayList<>();
        if (request.neighbourCells != null) {
            for (GridCellDTO cell : request.neighbourCells) {
                if (cell.gridCellId == null || cell.maxCapacity == null) continue;
                ZoneMetrics m = new ZoneMetrics(cell.gridCellId, cell.maxCapacity);
                accumulate(m, byZone.getOrDefault(cell.gridCellId, Collections.emptyList()));
                m.compute(thresholdPercent);
                neighbours.add(m);
            }
        }

        return Collections.singletonList(createRecommendation(source, neighbours, timestamp, thresholdPercent));
    }

    public GridCellEvaluationResult evaluateCell(SingleCellEvaluationRequest request) {
        if (request.gridCell == null || request.gridCell.maxCapacity == null) {
            return new GridCellEvaluationResult(false);
        }
        GridCellDTO cell = request.gridCell;

        double demandKw = 0;
        double supplyKw = 0;
        for (TelemetryDTO t : request.telemetryData) {
            if ("EV_CHARGER".equals(t.asset_type)) {
                demandKw += valueOrZero(t.Charging_Rate);
            } else if ("SOLAR".equals(t.asset_type)) {
                supplyKw += valueOrZero(t.Current_Generation);
            } else if ("BATTERY".equals(t.asset_type)) {
                double output = valueOrZero(t.Current_Output);
                if (output > 0) supplyKw += output;
                else demandKw += Math.abs(output);
            }
        }

        double netLoadKw = Math.max(0, demandKw - supplyKw);
        boolean overThreshold = netLoadKw > cell.maxCapacity * thresholdPercent;
        return new GridCellEvaluationResult(overThreshold);
    }

    public Uni<BalancingRecommendation> emitRecommendation(BalancingRecommendation recommendation) {
        return persistAndEmit(recommendation);
    }

    public Uni<List<BalancingRecommendation>> emitAll(List<BalancingRecommendation> recommendations) {
        return persistAll(recommendations);
    }

    private void accumulate(ZoneMetrics metrics, List<TelemetryDTO> telemetry) {
        for (TelemetryDTO t : telemetry) {
            if ("EV_CHARGER".equals(t.asset_type)) {
                metrics.demandKw += valueOrZero(t.Charging_Rate);
            } else if ("SOLAR".equals(t.asset_type)) {
                metrics.supplyKw += valueOrZero(t.Current_Generation);
            } else if ("BATTERY".equals(t.asset_type)) {
                double output = valueOrZero(t.Current_Output);
                if (output > 0) metrics.supplyKw += output;
                else metrics.demandKw += Math.abs(output);
            }
        }
    }

    private Uni<List<BalancingRecommendation>> persistAll(List<BalancingRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            return Uni.createFrom().item(recommendations);
        }

        List<Uni<BalancingRecommendation>> operations = recommendations.stream()
                .map(this::persistAndEmit)
                .collect(Collectors.toList());

        return Uni.combine().all().unis(operations)
                .combinedWith(results -> results.stream()
                        .map(result -> (BalancingRecommendation) result)
                        .collect(Collectors.toList()));
    }

    private Uni<BalancingRecommendation> persistAndEmit(BalancingRecommendation recommendation) {
        return recommendation.save(client)
                .onItem().invoke(id -> recommendation.id = id)
                .onItem().invoke(id -> emitRecommendationIfNeeded(recommendation))
                .onItem().transform(id -> recommendation);
    }

    /** Status is NO_TARGET when no other zone has positive headroom. */
    private BalancingRecommendation createRecommendation(ZoneMetrics source,
                                                        List<ZoneMetrics> zones,
                                                        LocalDateTime timestamp,
                                                        double threshold) {
        ZoneMetrics target = zones.stream()
                .filter(zone -> !zone.gridCellId.equals(source.gridCellId))
                .filter(zone -> zone.headroomKw > 0)
                .max(Comparator.comparingDouble(zone -> zone.headroomKw))
                .orElse(null);

        double overloadKw = source.netLoadKw - source.thresholdLimitKw;
        Double transferableKw = target == null ? null : Math.min(overloadKw, target.headroomKw);
        Double targetHeadroomKw = target == null ? null : target.headroomKw;

        String status = target == null ? "NO_TARGET" : "RECOMMENDED";
        String rationale = target == null
                ? "Overload above threshold with no surplus zone available."
                : "Overload above threshold; target zone selected by max headroom.";

        return new BalancingRecommendation(
                null,
                source.gridCellId,
                target == null ? null : target.gridCellId,
                source.netLoadKw,
                targetHeadroomKw,
                overloadKw,
                transferableKw,
                threshold,
                status,
                rationale,
                timestamp
        );
    }

    /** Only RECOMMENDED status is published; NO_TARGET is not. Kafka key: sourceGridCellId */
    private void emitRecommendationIfNeeded(BalancingRecommendation recommendation) {
        if (!"RECOMMENDED".equals(recommendation.status)) {
            return;
        }

        String json = String.format(
                "{\"sourceGridCellId\":\"%s\",\"targetGridCellId\":\"%s\",\"overloadKw\":%.2f,\"transferableKw\":%.2f,\"thresholdPercent\":%.2f,\"timestamp\":\"%s\"}",
                recommendation.sourceGridCellId,
                recommendation.targetGridCellId,
                recommendation.overloadKw,
                recommendation.transferableKw == null ? 0.0 : recommendation.transferableKw,
                recommendation.thresholdPercent,
                recommendation.createdAt.toString()
        );

        recommendationEmitter.send(Message.of(json)
                .addMetadata(OutgoingKafkaRecordMetadata.builder()
                        .withKey(recommendation.sourceGridCellId)
                        .build()));
    }

    private double valueOrZero(Float value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private static class ZoneMetrics {
        final String gridCellId;
        final double maxCapacityKw;
        double demandKw;
        double supplyKw;
        double netLoadKw;
        double thresholdLimitKw;
        double headroomKw;

        ZoneMetrics(String gridCellId, double maxCapacityKw) {
            this.gridCellId = gridCellId;
            this.maxCapacityKw = maxCapacityKw;
        }

        void compute(double threshold) {
            thresholdLimitKw = maxCapacityKw * threshold;
            netLoadKw = Math.max(0, demandKw - supplyKw);
            headroomKw = thresholdLimitKw - netLoadKw;
        }
    }
}
