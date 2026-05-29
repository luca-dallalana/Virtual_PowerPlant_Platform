package org.acme.services;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.GridCellDTO;
import org.acme.dto.TelemetryDTO;
import org.acme.entities.BalancingRecommendation;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    public Uni<List<BalancingRecommendation>> calculateRecommendationsFromEvents(List<TelemetryDTO> telemetryList, List<GridCellDTO> gridCells) {
        LocalDateTime now = LocalDateTime.now();
        List<BalancingRecommendation> recommendations = buildRecommendations(telemetryList, gridCells, now, thresholdPercent);
        return persistAll(recommendations);
    }

    /** Grid cells missing gridCellId or maxCapacity are skipped; unrecognized telemetry zones are ignored. */
    private List<BalancingRecommendation> buildRecommendations(List<TelemetryDTO> telemetryList,
                                                               List<GridCellDTO> gridCells,
                                                               LocalDateTime timestamp,
                                                               double threshold) {
        Map<String, ZoneMetrics> metricsByZone = new HashMap<>();
        for (GridCellDTO cell : gridCells) {
            if (cell.gridCellId == null || cell.maxCapacity == null) {
                continue;
            }
            metricsByZone.put(cell.gridCellId, new ZoneMetrics(cell.gridCellId, cell.maxCapacity));
        }

        for (TelemetryDTO telemetry : telemetryList) {
            if (telemetry.grid_cell_id == null) {
                continue;
            }
            ZoneMetrics metrics = metricsByZone.get(telemetry.grid_cell_id);
            if (metrics == null) {
                continue;
            }

            String type = telemetry.asset_type;
            if ("EV_CHARGER".equals(type)) {
                metrics.demandKw += valueOrZero(telemetry.Charging_Rate);
            } else if ("SOLAR".equals(type)) {
                metrics.supplyKw += valueOrZero(telemetry.Current_Generation);
            } else if ("BATTERY".equals(type)) {
                double output = valueOrZero(telemetry.Current_Output);
                if (output > 0) {
                    metrics.supplyKw += output;
                } else {
                    metrics.demandKw += Math.abs(output);
                }
            }
        }

        for (ZoneMetrics metrics : metricsByZone.values()) {
            metrics.compute(threshold);
        }

        List<ZoneMetrics> zones = new ArrayList<>(metricsByZone.values());
        return zones.stream()
                .filter(metrics -> metrics.netLoadKw > metrics.thresholdLimitKw)
                .map(metrics -> createRecommendation(metrics, zones, timestamp, threshold))
                .collect(Collectors.toList());
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
