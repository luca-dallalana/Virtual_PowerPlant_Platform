package org.acme.services;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
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
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class AnalyticsCalculationService {

    private static final double WINDOW_HOURS = 0.5;

    @Inject
    MySQLPool client;

    @Inject
    @Channel("energy-discharged-zone")
    Emitter<String> dischargedZoneEmitter;

    @Inject
    @Channel("energy-generated-prosumer")
    Emitter<String> generatedProsumerEmitter;

    @Inject
    @Channel("energy-consumed-prosumer")
    Emitter<String> consumedProsumerEmitter;

    @Inject
    @Channel("average-soc")
    Emitter<String> averageSocEmitter;

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

    public Uni<AnalyticsResult> emitAnalytics(
            List<GeneratedEnergyByProsumer> generated,
            List<ConsumedEnergyByProsumer> consumed,
            List<EnergyDischargedByZone> discharged,
            AverageSoC averageSoC) {

        LocalDateTime timestamp = LocalDateTime.now();
        List<Uni<Long>> saveOps = new ArrayList<>();

        if (generated != null) {
            for (GeneratedEnergyByProsumer g : generated) {
                saveOps.add(g.save(client).invoke(() ->
                    publishGeneratedProsumerEvent(g.prosumerId, g.totalEnergyGeneratedKwh, g.solarAssetCount, g.timestamp)));
            }
        }
        if (consumed != null) {
            for (ConsumedEnergyByProsumer c : consumed) {
                saveOps.add(c.save(client).invoke(() ->
                    publishConsumedProsumerEvent(c.prosumerId, c.totalEnergyConsumedKwh, c.evChargerCount, c.timestamp)));
            }
        }
        if (discharged != null) {
            for (EnergyDischargedByZone d : discharged) {
                saveOps.add(d.save(client).invoke(() ->
                    publishDischargedZoneEvent(d.gridCellId, d.totalEnergyDischargedKwh, d.batteryCount, d.timestamp)));
            }
        }
        if (averageSoC != null) {
            saveOps.add(averageSoC.save(client).invoke(() ->
                publishAverageSocEvent(averageSoC.averageSocPercent, averageSoC.batteryCount, averageSoC.timestamp)));
        }

        if (saveOps.isEmpty()) {
            return Uni.createFrom().item(new AnalyticsResult("SUCCESS", timestamp, 0));
        }

        int totalRecords = (generated != null ? generated.size() : 0)
            + (consumed != null ? consumed.size() : 0)
            + (discharged != null ? discharged.size() : 0)
            + (averageSoC != null ? 1 : 0);

        return Uni.combine().all().unis(saveOps).combinedWith(results ->
            new AnalyticsResult("SUCCESS", timestamp, totalRecords));
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

    private void publishDischargedZoneEvent(String gridCellId, double totalDischarge, int batteryCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"gridCellId\":\"%s\",\"totalDischargeKwh\":%.4f,\"batteryCount\":%d,\"timestamp\":\"%s\"}",
            gridCellId, totalDischarge, batteryCount, timestamp.toString()
        );
        dischargedZoneEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(gridCellId).build()));
    }

    private void publishGeneratedProsumerEvent(Long prosumerId, double totalGeneration, int assetCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"prosumerId\":%d,\"totalGenerationKwh\":%.4f,\"assetCount\":%d,\"timestamp\":\"%s\"}",
            prosumerId, totalGeneration, assetCount, timestamp.toString()
        );
        generatedProsumerEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(prosumerId.toString()).build()));
    }

    private void publishConsumedProsumerEvent(Long prosumerId, double totalConsumption, int chargerCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"prosumerId\":%d,\"totalConsumptionKwh\":%.4f,\"chargerCount\":%d,\"timestamp\":\"%s\"}",
            prosumerId, totalConsumption, chargerCount, timestamp.toString()
        );
        consumedProsumerEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(prosumerId.toString()).build()));
    }

    private void publishAverageSocEvent(double averageSoC, int batteryCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"averageSoC\":%.2f,\"batteryCount\":%d,\"timestamp\":\"%s\"}",
            averageSoC, batteryCount, timestamp.toString()
        );
        averageSocEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey("system").build()));
    }
}
