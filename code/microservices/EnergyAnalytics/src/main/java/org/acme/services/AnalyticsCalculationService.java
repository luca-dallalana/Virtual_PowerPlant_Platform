package org.acme.services;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.dto.AnalyticsResult;
import org.acme.dto.AssetLinkDTO;
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
import java.util.stream.Collectors;

/**
 * Aggregates telemetry into four metrics (energy discharged by zone, generated/consumed by prosumer,
 * fleet SoC average), persists each to MySQL, and publishes the corresponding Kafka event.
 * All four calculations run concurrently.
 */
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

    /**
     * Builds an asset-id-to-link index (first link wins on duplicates) and runs the calculation pipeline.
     */
    public Uni<AnalyticsResult> calculateMetricsFromEvents(List<TelemetryDTO> telemetryList, List<AssetLinkDTO> assetLinkList) {
        LocalDateTime timestamp = LocalDateTime.now();
        String aggregationPeriod = "CURRENT";

        Map<Long, AssetLinkDTO> assetLinkMap = assetLinkList.stream()
            .collect(Collectors.toMap(link -> link.assetId, link -> link, (a, b) -> a));

        return calculateAndSave(telemetryList, assetLinkMap, timestamp, aggregationPeriod);
    }

    /** Average-SoC always produces at most one record, so it contributes 0 or 1 to totalRecords. */
    private Uni<AnalyticsResult> calculateAndSave(List<TelemetryDTO> telemetryList,
                                                  Map<Long, AssetLinkDTO> assetLinkMap,
                                                  LocalDateTime timestamp,
                                                  String aggregationPeriod) {
        return Uni.combine().all().unis(
            calculateEnergyDischargedByZone(telemetryList, assetLinkMap, timestamp, aggregationPeriod),
            calculateGeneratedEnergyByProsumer(telemetryList, assetLinkMap, timestamp, aggregationPeriod),
            calculateConsumedEnergyByProsumer(telemetryList, assetLinkMap, timestamp, aggregationPeriod),
            calculateAverageSoC(telemetryList, timestamp, aggregationPeriod)
        ).combinedWith((discharged, generated, consumed, avgSoc) -> {
            int totalRecords = discharged + generated + consumed + (avgSoc > 0 ? 1 : 0);
            return new AnalyticsResult("SUCCESS", timestamp, totalRecords);
        });
    }

    /** Groups BATTERY assets by grid cell, sums positive Current_Output, persists and publishes one record per zone. */
    private Uni<Integer> calculateEnergyDischargedByZone(List<TelemetryDTO> telemetryList,
                                                         Map<Long, AssetLinkDTO> assetLinkMap,
                                                         LocalDateTime timestamp,
                                                         String aggregationPeriod) {
        Map<String, List<TelemetryDTO>> groupedByZone = telemetryList.stream()
            .filter(t -> "BATTERY".equals(t.asset_type))
            .filter(t -> t.Current_Output != null && t.Current_Output > 0)
            .filter(t -> assetLinkMap.containsKey(t.asset_id))
            .collect(Collectors.groupingBy(t -> assetLinkMap.get(t.asset_id).gridCellId));

        List<Uni<Long>> saveOperations = new ArrayList<>();

        for (Map.Entry<String, List<TelemetryDTO>> entry : groupedByZone.entrySet()) {
            String gridCellId = entry.getKey();
            List<TelemetryDTO> batteries = entry.getValue();

            double totalDischarged = batteries.stream()
                .mapToDouble(t -> t.Current_Output != null ? t.Current_Output : 0.0)
                .sum();

            EnergyDischargedByZone record = new EnergyDischargedByZone(
                null, gridCellId, totalDischarged, batteries.size(), timestamp, aggregationPeriod
            );

            Uni<Long> saveUni = record.save(client)
                .invoke(() -> publishDischargedZoneEvent(gridCellId, totalDischarged, batteries.size(), timestamp));

            saveOperations.add(saveUni);
        }

        return Uni.combine().all().unis(saveOperations).combinedWith(results -> results.size());
    }

    /** Groups SOLAR assets by prosumer, sums Current_Generation, persists and publishes one record per prosumer. */
    private Uni<Integer> calculateGeneratedEnergyByProsumer(List<TelemetryDTO> telemetryList,
                                                           Map<Long, AssetLinkDTO> assetLinkMap,
                                                           LocalDateTime timestamp,
                                                           String aggregationPeriod) {
        Map<Long, List<TelemetryDTO>> groupedByProsumer = telemetryList.stream()
            .filter(t -> "SOLAR".equals(t.asset_type))
            .filter(t -> assetLinkMap.containsKey(t.asset_id))
            .collect(Collectors.groupingBy(t -> assetLinkMap.get(t.asset_id).prosumerId));

        List<Uni<Long>> saveOperations = new ArrayList<>();

        for (Map.Entry<Long, List<TelemetryDTO>> entry : groupedByProsumer.entrySet()) {
            Long prosumerId = entry.getKey();
            List<TelemetryDTO> solarAssets = entry.getValue();

            double totalGenerated = solarAssets.stream()
                .mapToDouble(t -> t.Current_Generation != null ? t.Current_Generation : 0.0)
                .sum();

            GeneratedEnergyByProsumer record = new GeneratedEnergyByProsumer(
                null, prosumerId, totalGenerated, solarAssets.size(), timestamp, aggregationPeriod
            );

            Uni<Long> saveUni = record.save(client)
                .invoke(() -> publishGeneratedProsumerEvent(prosumerId, totalGenerated, solarAssets.size(), timestamp));

            saveOperations.add(saveUni);
        }

        return Uni.combine().all().unis(saveOperations).combinedWith(results -> results.size());
    }

    /** Groups EV_CHARGER assets by prosumer, sums Charging_Rate, persists and publishes one record per prosumer. */
    private Uni<Integer> calculateConsumedEnergyByProsumer(List<TelemetryDTO> telemetryList,
                                                          Map<Long, AssetLinkDTO> assetLinkMap,
                                                          LocalDateTime timestamp,
                                                          String aggregationPeriod) {
        Map<Long, List<TelemetryDTO>> groupedByProsumer = telemetryList.stream()
            .filter(t -> "EV_CHARGER".equals(t.asset_type))
            .filter(t -> assetLinkMap.containsKey(t.asset_id))
            .collect(Collectors.groupingBy(t -> assetLinkMap.get(t.asset_id).prosumerId));

        List<Uni<Long>> saveOperations = new ArrayList<>();

        for (Map.Entry<Long, List<TelemetryDTO>> entry : groupedByProsumer.entrySet()) {
            Long prosumerId = entry.getKey();
            List<TelemetryDTO> evChargers = entry.getValue();

            double totalConsumed = evChargers.stream()
                .mapToDouble(t -> t.Charging_Rate != null ? t.Charging_Rate : 0.0)
                .sum();

            ConsumedEnergyByProsumer record = new ConsumedEnergyByProsumer(
                null, prosumerId, totalConsumed, evChargers.size(), timestamp, aggregationPeriod
            );

            Uni<Long> saveUni = record.save(client)
                .invoke(() -> publishConsumedProsumerEvent(prosumerId, totalConsumed, evChargers.size(), timestamp));

            saveOperations.add(saveUni);
        }

        return Uni.combine().all().unis(saveOperations).combinedWith(results -> results.size());
    }

    /** Averages State_of_Charge across all non-null BATTERY readings; defaults to 0.0 if none present. */
    private Uni<Integer> calculateAverageSoC(List<TelemetryDTO> telemetryList,
                                            LocalDateTime timestamp,
                                            String aggregationPeriod) {
        List<TelemetryDTO> batteries = telemetryList.stream()
            .filter(t -> "BATTERY".equals(t.asset_type))
            .filter(t -> t.State_of_Charge != null)
            .collect(Collectors.toList());

        double avgSoc = batteries.stream()
            .mapToDouble(t -> t.State_of_Charge)
            .average()
            .orElse(0.0);

        AverageSoC record = new AverageSoC(
            null, avgSoc, batteries.size(), timestamp, aggregationPeriod
        );

        return record.save(client)
            .invoke(() -> publishAverageSocEvent(avgSoc, batteries.size(), timestamp))
            .map(id -> 1);
    }

    /** Kafka key: gridCellId */
    private void publishDischargedZoneEvent(String gridCellId, double totalDischarge, int batteryCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"gridCellId\":\"%s\",\"totalDischarge\":%.2f,\"batteryCount\":%d,\"timestamp\":\"%s\"}",
            gridCellId, totalDischarge, batteryCount, timestamp.toString()
        );
        dischargedZoneEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(gridCellId).build()));
    }

    /** Kafka key: prosumerId */
    private void publishGeneratedProsumerEvent(Long prosumerId, double totalGeneration, int assetCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"prosumerId\":%d,\"totalGeneration\":%.2f,\"assetCount\":%d,\"timestamp\":\"%s\"}",
            prosumerId, totalGeneration, assetCount, timestamp.toString()
        );
        generatedProsumerEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(prosumerId.toString()).build()));
    }

    /** Kafka key: prosumerId */
    private void publishConsumedProsumerEvent(Long prosumerId, double totalConsumption, int chargerCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"prosumerId\":%d,\"totalConsumption\":%.2f,\"chargerCount\":%d,\"timestamp\":\"%s\"}",
            prosumerId, totalConsumption, chargerCount, timestamp.toString()
        );
        consumedProsumerEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(prosumerId.toString()).build()));
    }

    /** Kafka key: "system" (fleet-level aggregate, not per-asset) */
    private void publishAverageSocEvent(double averageSoC, int batteryCount, LocalDateTime timestamp) {
        String json = String.format(
            "{\"averageSoC\":%.2f,\"batteryCount\":%d,\"timestamp\":\"%s\"}",
            averageSoC, batteryCount, timestamp.toString()
        );
        averageSocEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey("system").build()));
    }
}
