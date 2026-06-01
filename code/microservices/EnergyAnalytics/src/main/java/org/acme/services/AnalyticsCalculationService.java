package org.acme.services;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.AnalyticsResult;
import org.acme.dto.AssetDTO;
import org.acme.dto.TelemetryDTO;
import org.acme.dto.ZoneDTO;
import org.acme.entities.AverageSoC;
import org.acme.entities.ConsumedEnergyByProsumer;
import org.acme.entities.EnergyDischargedByZone;
import org.acme.entities.GeneratedEnergyByProsumer;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class AnalyticsCalculationService {

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

        return telemetry.stream()
            .filter(t -> "SOLAR".equals(t.asset_type))
            .filter(t -> assetToProsumerMap.containsKey(t.asset_id))
            .collect(Collectors.groupingBy(t -> assetToProsumerMap.get(t.asset_id)))
            .entrySet().stream()
            .map(entry -> {
                List<TelemetryDTO> solar = entry.getValue();
                double total = solar.stream()
                    .mapToDouble(t -> t.Current_Generation != null ? t.Current_Generation : 0.0)
                    .sum();
                return new GeneratedEnergyByProsumer(null, entry.getKey(), total, solar.size(), timestamp, "LAST_30_MIN");
            })
            .collect(Collectors.toList());
    }

    public List<ConsumedEnergyByProsumer> computeConsumedByProsumer(List<AssetDTO> assets, List<TelemetryDTO> telemetry) {
        LocalDateTime timestamp = LocalDateTime.now();
        Map<Long, Long> assetToProsumerMap = buildAssetToProsumerMap(assets);

        return telemetry.stream()
            .filter(t -> "EV_CHARGER".equals(t.asset_type))
            .filter(t -> assetToProsumerMap.containsKey(t.asset_id))
            .collect(Collectors.groupingBy(t -> assetToProsumerMap.get(t.asset_id)))
            .entrySet().stream()
            .map(entry -> {
                List<TelemetryDTO> evs = entry.getValue();
                double total = evs.stream()
                    .mapToDouble(t -> t.Charging_Rate != null ? t.Charging_Rate : 0.0)
                    .sum();
                return new ConsumedEnergyByProsumer(null, entry.getKey(), total, evs.size(), timestamp, "LAST_30_MIN");
            })
            .collect(Collectors.toList());
    }

    public List<EnergyDischargedByZone> computeDischargedByZone(List<ZoneDTO> zones, List<TelemetryDTO> telemetry) {
        LocalDateTime timestamp = LocalDateTime.now();

        return telemetry.stream()
            .filter(t -> "BATTERY".equals(t.asset_type))
            .filter(t -> t.Current_Output != null && t.Current_Output > 0)
            .filter(t -> t.grid_cell_id != null)
            .collect(Collectors.groupingBy(t -> t.grid_cell_id))
            .entrySet().stream()
            .map(entry -> {
                List<TelemetryDTO> batteries = entry.getValue();
                double total = batteries.stream()
                    .mapToDouble(t -> t.Current_Output != null ? t.Current_Output : 0.0)
                    .sum();
                return new EnergyDischargedByZone(null, entry.getKey(), total, batteries.size(), timestamp, "LAST_30_MIN");
            })
            .collect(Collectors.toList());
    }

    public AverageSoC computeAverageSoC(List<TelemetryDTO> telemetry) {
        LocalDateTime timestamp = LocalDateTime.now();
        List<TelemetryDTO> batteries = telemetry.stream()
            .filter(t -> "BATTERY".equals(t.asset_type))
            .filter(t -> t.State_of_Charge != null)
            .collect(Collectors.toList());

        double avg = batteries.stream()
            .mapToDouble(t -> t.State_of_Charge)
            .average()
            .orElse(0.0);

        return new AverageSoC(null, avg, batteries.size(), timestamp, "LAST_30_MIN");
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
                    publishGeneratedProsumerEvent(g.prosumerId, g.totalEnergyGeneratedKw, g.solarAssetCount, g.timestamp)));
            }
        }
        if (consumed != null) {
            for (ConsumedEnergyByProsumer c : consumed) {
                saveOps.add(c.save(client).invoke(() ->
                    publishConsumedProsumerEvent(c.prosumerId, c.totalEnergyConsumedKw, c.evChargerCount, c.timestamp)));
            }
        }
        if (discharged != null) {
            for (EnergyDischargedByZone d : discharged) {
                saveOps.add(d.save(client).invoke(() ->
                    publishDischargedZoneEvent(d.gridCellId, d.totalEnergyDischargedKw, d.batteryCount, d.timestamp)));
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

    private Map<Long, Long> buildAssetToProsumerMap(List<AssetDTO> assets) {
        if (assets == null) return Collections.emptyMap();
        return assets.stream()
            .collect(Collectors.toMap(a -> a.assetId, a -> a.prosumerId, (a, b) -> a));
    }

    private void publishDischargedZoneEvent(String gridCellId, double totalDischarge, int batteryCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"gridCellId\":\"%s\",\"totalDischarge\":%.2f,\"batteryCount\":%d,\"timestamp\":\"%s\"}",
            gridCellId, totalDischarge, batteryCount, timestamp.toString()
        );
        dischargedZoneEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(gridCellId).build()));
    }

    private void publishGeneratedProsumerEvent(Long prosumerId, double totalGeneration, int assetCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"prosumerId\":%d,\"totalGeneration\":%.2f,\"assetCount\":%d,\"timestamp\":\"%s\"}",
            prosumerId, totalGeneration, assetCount, timestamp.toString()
        );
        generatedProsumerEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(prosumerId.toString()).build()));
    }

    private void publishConsumedProsumerEvent(Long prosumerId, double totalConsumption, int chargerCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"prosumerId\":%d,\"totalConsumption\":%.2f,\"chargerCount\":%d,\"timestamp\":\"%s\"}",
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
