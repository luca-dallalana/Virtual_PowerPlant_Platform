package org.acme.consumers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

import org.acme.dto.TelemetryDTO;
import org.acme.dto.AssetLinkDTO;
import org.acme.services.AnalyticsCalculationService;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

public class TelemetryEventProcessor extends Thread {
    private String kafka_servers;
    private AnalyticsCalculationService analyticsService;
    private MySQLPool client;
    private final AtomicInteger eventCounter = new AtomicInteger(0);
    private final Map<String, TelemetryDTO> telemetryBuffer = new ConcurrentHashMap<>();
    private long lastCalculation = System.currentTimeMillis();

    private static final int CALCULATION_THRESHOLD = 20;
    private static final long CALCULATION_INTERVAL_MS = 60000;

    public TelemetryEventProcessor(String kafka_servers,
                                  AnalyticsCalculationService analyticsService,
                                  MySQLPool client) {
        this.kafka_servers = kafka_servers;
        this.analyticsService = analyticsService;
        this.client = client;
    }

    public void run() {
        try {
            Properties properties = new Properties();
            properties.put("bootstrap.servers", kafka_servers);
            properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("group.id", "energy-analytics-telemetry-group");

            try (Consumer<String, String> consumer = new KafkaConsumer<>(properties)) {
                consumer.subscribe(Arrays.asList("Telemetry-Batteries", "Telemetry-Solar", "Telemetry-Chargers"));

                System.out.println("TelemetryEventProcessor: Subscribed to Telemetry-Batteries, Telemetry-Solar, Telemetry-Chargers for EnergyAnalytics");

                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        TelemetryDTO telemetry = parseTelemetryEvent(record.value());
                        telemetryBuffer.put(telemetry.asset_id.toString(), telemetry);

                        int count = eventCounter.incrementAndGet();
                        long now = System.currentTimeMillis();

                        if (count >= CALCULATION_THRESHOLD || (now - lastCalculation) >= CALCULATION_INTERVAL_MS) {
                            if (telemetryBuffer.size() > 0) {
                                calculateAnalytics();
                                eventCounter.set(0);
                                lastCalculation = now;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Exception in TelemetryEventProcessor (EnergyAnalytics): " + e);
            e.printStackTrace();
        }
    }

    private TelemetryDTO parseTelemetryEvent(String jsonString) {
        JSONObject obj = new JSONObject(jsonString);
        TelemetryDTO telemetry = new TelemetryDTO();

        telemetry.asset_id = Long.parseLong(obj.getString("asset_id"));
        telemetry.asset_type = obj.getString("asset_type");
        telemetry.grid_cell_id = obj.getString("grid_cell_id");
        telemetry.timeStamp = LocalDateTime.parse(obj.getString("timeStamp"), DateTimeFormatter.ISO_DATE_TIME);

        JSONObject payload = obj.getJSONObject("payload");

        if ("BATTERY".equals(telemetry.asset_type)) {
            telemetry.State_of_Charge = payload.optFloat("soc_percent", 0.0f);
            telemetry.Available_Energy = payload.optFloat("energy_available_kwh", 0.0f);
            telemetry.Current_Output = payload.optFloat("active_power_kw", 0.0f);
            telemetry.Max_Capacity = payload.optFloat("max_discharge_power_kw", 0.0f);
            telemetry.State_of_Health = payload.optFloat("soh_percent", 0.0f);
            telemetry.Status = payload.optString("connection_status", "");
        } else if ("SOLAR".equals(telemetry.asset_type)) {
            telemetry.Current_Generation = payload.optFloat("generation_kw", 0.0f);
            telemetry.Daily_Total = payload.optFloat("daily_yield_kwh", 0.0f);
            telemetry.Grid_Voltage = payload.optFloat("ac_voltage_v", 0.0f);
            telemetry.Frequency = payload.optFloat("grid_frequency_hz", 0.0f);
        } else if ("EV_CHARGER".equals(telemetry.asset_type)) {
            telemetry.Plug_Status = payload.optString("connector_status", "");
            telemetry.Charging_Rate = payload.optFloat("charging_power_kw", 0.0f);
            telemetry.Session_Energy = payload.optFloat("session_energy_kwh", 0.0f);
            telemetry.EV_SoC = payload.optFloat("ev_soc_percent", 0.0f);
        }

        return telemetry;
    }

    private void calculateAnalytics() {
        try {
            List<TelemetryDTO> telemetryList = new ArrayList<>(telemetryBuffer.values());

            List<AssetLinkDTO> assetLinks = client.query("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink")
                .execute()
                .map(rows -> {
                    List<AssetLinkDTO> links = new ArrayList<>();
                    rows.forEach(row -> {
                        AssetLinkDTO link = new AssetLinkDTO();
                        link.assetLinkId = row.getLong("assetLinkId");
                        link.assetId = row.getLong("assetId");
                        link.prosumerId = row.getLong("prosumerId");
                        link.utilityOperatorId = row.getLong("utilityOperatorId");
                        link.gridCellId = row.getString("gridCellId");
                        link.status = row.getString("status");
                        links.add(link);
                    });
                    return links;
                })
                .await().indefinitely();

            System.out.printf("EnergyAnalytics: Calculating metrics with %d telemetry events and %d asset links\n",
                telemetryList.size(), assetLinks.size());

            analyticsService.calculateMetricsFromEvents(telemetryList, assetLinks)
                .subscribe().with(
                    result -> System.out.printf("EnergyAnalytics: Calculated metrics successfully, total records: %d\n", result.totalRecords),
                    failure -> System.err.println("EnergyAnalytics: Failed to calculate metrics: " + failure.getMessage())
                );
        } catch (Exception e) {
            System.err.println("EnergyAnalytics: Error calculating analytics: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
