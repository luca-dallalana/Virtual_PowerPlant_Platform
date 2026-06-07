package org.acme.services;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.AnalyticsResult;
import org.acme.dto.AssetDTO;
import org.acme.dto.GridCellDTO;
import org.acme.dto.TelemetryDTO;
import org.acme.entities.AverageSoC;
import org.acme.entities.ConsumedEnergyByProsumer;
import org.acme.entities.EnergyDischargedByZone;
import org.acme.entities.GeneratedEnergyByProsumer;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class AnalyticsCalculationService {

    private static final double WINDOW_HOURS = 0.5;

    @Inject
    MySQLPool client;

    public List<GeneratedEnergyByProsumer> computeGeneratedByProsumer(List<AssetDTO> assets, List<TelemetryDTO> telemetry) {
        LocalDateTime timestamp = LocalDateTime.now();
        Map<Long, Long> assetToProsumerMap = buildAssetToProsumerMap(assets);

        Map<Long, List<TelemetryDTO>> byAsset = telemetry.stream()
            .filter(t -> "SOLAR".equals(t.asset_type))
            .filter(t -> t.asset_id != null && assetToProsumerMap.containsKey(t.asset_id))
            .collect(Collectors.groupingBy(t -> t.asset_id));

        Map<Long, Double> energyByProsumer = new HashMap<>();
        Map<Long, Integer> assetCountByProsumer = new HashMap<>();

        for (Map.Entry<Long, List<TelemetryDTO>> entry : byAsset.entrySet()) {
            Long prosumerId = assetToProsumerMap.get(entry.getKey());
            double energyKwh = avgPowerOverWindow(entry.getValue(), t -> t.Current_Generation);
            energyByProsumer.merge(prosumerId, energyKwh, Double::sum);
            assetCountByProsumer.merge(prosumerId, 1, Integer::sum);
        }

        return energyByProsumer.entrySet().stream()
            .map(e -> new GeneratedEnergyByProsumer(null, e.getKey(), e.getValue(),
                assetCountByProsumer.getOrDefault(e.getKey(), 0), timestamp, "LAST_30_MIN"))
            .collect(Collectors.toList());
    }

    public List<ConsumedEnergyByProsumer> computeConsumedByProsumer(List<AssetDTO> assets, List<TelemetryDTO> telemetry) {
        LocalDateTime timestamp = LocalDateTime.now();
        Map<Long, Long> assetToProsumerMap = buildAssetToProsumerMap(assets);

        Map<Long, List<TelemetryDTO>> byAsset = telemetry.stream()
            .filter(t -> "EV_CHARGER".equals(t.asset_type))
            .filter(t -> t.asset_id != null && assetToProsumerMap.containsKey(t.asset_id))
            .collect(Collectors.groupingBy(t -> t.asset_id));

        Map<Long, Double> energyByProsumer = new HashMap<>();
        Map<Long, Integer> assetCountByProsumer = new HashMap<>();

        for (Map.Entry<Long, List<TelemetryDTO>> entry : byAsset.entrySet()) {
            Long prosumerId = assetToProsumerMap.get(entry.getKey());
            double energyKwh = avgPowerOverWindow(entry.getValue(), t -> t.Charging_Rate);
            energyByProsumer.merge(prosumerId, energyKwh, Double::sum);
            assetCountByProsumer.merge(prosumerId, 1, Integer::sum);
        }

        return energyByProsumer.entrySet().stream()
            .map(e -> new ConsumedEnergyByProsumer(null, e.getKey(), e.getValue(),
                assetCountByProsumer.getOrDefault(e.getKey(), 0), timestamp, "LAST_30_MIN"))
            .collect(Collectors.toList());
    }

    public List<EnergyDischargedByZone> computeDischargedByZone(List<GridCellDTO> zones, List<TelemetryDTO> telemetry) {
        LocalDateTime timestamp = LocalDateTime.now();

        Map<String, GridCellDTO> zoneMap = new HashMap<>();
        if (zones != null) {
            for (GridCellDTO z : zones) {
                if (z.gridCellId != null) zoneMap.put(z.gridCellId, z);
            }
        }

        Map<String, Map<Long, List<TelemetryDTO>>> byZoneByAsset = new HashMap<>();
        for (TelemetryDTO t : telemetry) {
            if (!"BATTERY".equals(t.asset_type)) continue;
            if (t.grid_cell_id == null || !zoneMap.containsKey(t.grid_cell_id)) continue;
            byZoneByAsset
                .computeIfAbsent(t.grid_cell_id, k -> new HashMap<>())
                .computeIfAbsent(t.asset_id, k -> new ArrayList<>())
                .add(t);
        }

        List<EnergyDischargedByZone> results = new ArrayList<>();
        for (String zoneId : zoneMap.keySet()) {
            Map<Long, List<TelemetryDTO>> assetReadings = byZoneByAsset.get(zoneId);
            if (assetReadings == null || assetReadings.isEmpty()) {
                results.add(new EnergyDischargedByZone(null, zoneId, 0.0, 0, timestamp, "LAST_30_MIN"));
            } else {
                double totalEnergy = 0.0;
                for (List<TelemetryDTO> readings : assetReadings.values()) {
                    totalEnergy += avgPowerOverWindow(readings,
                        t -> t.Current_Output != null && t.Current_Output > 0 ? t.Current_Output : 0f);
                }
                results.add(new EnergyDischargedByZone(null, zoneId, totalEnergy, assetReadings.size(), timestamp, "LAST_30_MIN"));
            }
        }
        return results;
    }

    public AverageSoC computeAverageSoC(List<TelemetryDTO> telemetry) {
        LocalDateTime timestamp = LocalDateTime.now();

        Map<Long, Double> avgSocByAsset = telemetry.stream()
            .filter(t -> "BATTERY".equals(t.asset_type))
            .filter(t -> t.asset_id != null && t.State_of_Charge != null)
            .collect(Collectors.groupingBy(
                t -> t.asset_id,
                Collectors.averagingDouble(t -> t.State_of_Charge)
            ));

        double avg = avgSocByAsset.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);

        return new AverageSoC(null, avg, avgSocByAsset.size(), timestamp, "LAST_30_MIN");
    }

    public Uni<AnalyticsResult> persistConsumed(List<ConsumedEnergyByProsumer> list) {
        if (list == null || list.isEmpty()) return Uni.createFrom().item(new AnalyticsResult("SUCCESS", LocalDateTime.now(), 0));
        List<Uni<Long>> saves = list.stream().map(c -> c.save(client)).collect(Collectors.toList());
        return Uni.combine().all().unis(saves).combinedWith(r -> new AnalyticsResult("SUCCESS", LocalDateTime.now(), r.size()));
    }

    public Uni<AnalyticsResult> persistGenerated(List<GeneratedEnergyByProsumer> list) {
        if (list == null || list.isEmpty()) return Uni.createFrom().item(new AnalyticsResult("SUCCESS", LocalDateTime.now(), 0));
        List<Uni<Long>> saves = list.stream().map(g -> g.save(client)).collect(Collectors.toList());
        return Uni.combine().all().unis(saves).combinedWith(r -> new AnalyticsResult("SUCCESS", LocalDateTime.now(), r.size()));
    }

    public Uni<AnalyticsResult> persistDischarged(List<EnergyDischargedByZone> list) {
        if (list == null || list.isEmpty()) return Uni.createFrom().item(new AnalyticsResult("SUCCESS", LocalDateTime.now(), 0));
        List<Uni<Long>> saves = list.stream().map(d -> d.save(client)).collect(Collectors.toList());
        return Uni.combine().all().unis(saves).combinedWith(r -> new AnalyticsResult("SUCCESS", LocalDateTime.now(), r.size()));
    }

    public Uni<AnalyticsResult> persistAverageSoC(AverageSoC averageSoC) {
        if (averageSoC == null) return Uni.createFrom().item(new AnalyticsResult("SUCCESS", LocalDateTime.now(), 0));
        return averageSoC.save(client).map(id -> new AnalyticsResult("SUCCESS", LocalDateTime.now(), 1));
    }

    /** Average power across all readings for the asset, multiplied by the 30-min window (0.5h) to give kWh. */
    private double avgPowerOverWindow(List<TelemetryDTO> readings, Function<TelemetryDTO, Float> powerExtractor) {
        if (readings == null || readings.isEmpty()) return 0.0;
        double avgPower = readings.stream()
            .mapToDouble(t -> { Float p = powerExtractor.apply(t); return p != null ? p : 0.0; })
            .average().orElse(0.0);
        return avgPower * WINDOW_HOURS;
    }

    private Map<Long, Long> buildAssetToProsumerMap(List<AssetDTO> assets) {
        if (assets == null) return Collections.emptyMap();
        return assets.stream()
            .collect(Collectors.toMap(a -> a.assetId, a -> a.prosumerId, (a, b) -> a));
    }
}
