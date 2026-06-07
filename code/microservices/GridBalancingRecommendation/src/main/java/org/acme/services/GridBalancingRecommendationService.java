package org.acme.services;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.BalancingRecommendationDTO;
import org.acme.dto.GridCellDTO;
import org.acme.dto.GridCellMetricsDTO;
import org.acme.dto.TelemetryDTO;
import org.acme.entities.BalancingRecommendation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class GridBalancingRecommendationService {

    @Inject
    MySQLPool client;

    public GridCellMetricsDTO computeSingleCellMetrics(GridCellDTO cell, List<TelemetryDTO> telemetry) {
        ZoneMetrics m = new ZoneMetrics(cell.gridCellId, cell.maxCapacity);
        accumulate(m, telemetry != null ? telemetry : Collections.emptyList());
        m.compute();
        return new GridCellMetricsDTO(m.gridCellId, m.maxCapacityKw, m.netLoadKw, m.headroomKw);
    }

    public List<GridCellMetricsDTO> computeMultiCellMetrics(List<GridCellDTO> cells, List<TelemetryDTO> allTelemetry) {
        List<TelemetryDTO> telemetry = allTelemetry != null ? allTelemetry : Collections.emptyList();
        Map<String, List<TelemetryDTO>> byZone = telemetry.stream()
                .filter(t -> t.grid_cell_id != null)
                .collect(Collectors.groupingBy(t -> t.grid_cell_id));

        List<GridCellMetricsDTO> results = new ArrayList<>();
        if (cells != null) {
            for (GridCellDTO cell : cells) {
                if (cell.gridCellId == null || cell.maxCapacity == null) continue;
                ZoneMetrics m = new ZoneMetrics(cell.gridCellId, cell.maxCapacity);
                accumulate(m, byZone.getOrDefault(cell.gridCellId, Collections.emptyList()));
                m.compute();
                results.add(new GridCellMetricsDTO(m.gridCellId, m.maxCapacityKw, m.netLoadKw, m.headroomKw));
            }
        }
        return results;
    }

    public Uni<List<BalancingRecommendation>> saveRecommendations(List<BalancingRecommendationDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Uni.createFrom().item(Collections.emptyList());
        }
        LocalDateTime now = LocalDateTime.now();
        List<Uni<BalancingRecommendation>> operations = dtos.stream()
                .map(dto -> {
                    BalancingRecommendation rec = new BalancingRecommendation(null, dto.assetId, dto.action, dto.from, dto.to, now, dto.cellContext, dto.socPercent, dto.isCharging, dto.assetType);
                    return rec.save(client)
                            .onItem().invoke(id -> rec.id = id)
                            .onItem().transform(id -> rec);
                })
                .collect(Collectors.toList());

        return Uni.combine().all().unis(operations)
                .combinedWith(results -> results.stream()
                        .map(r -> (BalancingRecommendation) r)
                        .collect(Collectors.toList()));
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

    private double valueOrZero(Float value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private static class ZoneMetrics {
        final String gridCellId;
        final double maxCapacityKw;
        double demandKw;
        double supplyKw;
        double netLoadKw;
        double headroomKw;

        ZoneMetrics(String gridCellId, double maxCapacityKw) {
            this.gridCellId = gridCellId;
            this.maxCapacityKw = maxCapacityKw;
        }

        void compute() {
            netLoadKw = Math.max(0, demandKw - supplyKw);
            headroomKw = maxCapacityKw - netLoadKw;
        }
    }
}
