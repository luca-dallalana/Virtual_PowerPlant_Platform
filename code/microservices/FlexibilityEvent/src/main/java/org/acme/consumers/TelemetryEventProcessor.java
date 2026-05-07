package org.acme.consumers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import org.json.*;

import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.acme.FlexibilityEvent;
import org.acme.TelemetryDTO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TelemetryEventProcessor extends Thread {
    private String kafka_servers;
    private io.vertx.mutiny.mysqlclient.MySQLPool client;
    private Emitter<String> flexibilityEmitter;

    public TelemetryEventProcessor(String kafka_servers, io.vertx.mutiny.mysqlclient.MySQLPool client, Emitter<String> flexibilityEmitter) {
        this.kafka_servers = kafka_servers;
        this.client = client;
        this.flexibilityEmitter = flexibilityEmitter;
    }

    public void run() {
        try {
            Properties properties = new Properties();
            properties.put("bootstrap.servers", kafka_servers);
            properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("group.id", "flexibility-event-group");

            try (Consumer<String, String> consumer = new KafkaConsumer<>(properties)) {
                consumer.subscribe(Collections.singletonList("Telemetry-Batteries"));

                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        System.out.printf("FlexibilityEvent - Received telemetry: topic = %s, key = %s\n",
                            record.topic(), record.key());

                        String jsonString = record.value();
                        TelemetryDTO telemetry = parseTelemetryEvent(jsonString);

                        evaluateFlexibilityRules(telemetry);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Exception in TelemetryEventProcessor: " + e);
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
        }

        return telemetry;
    }

    private void evaluateFlexibilityRules(TelemetryDTO telemetry) {
        if (telemetry.State_of_Charge != null && telemetry.State_of_Charge > 90.0) {
            FlexibilityEvent event = new FlexibilityEvent();
            event.assetId = telemetry.asset_id;
            event.prosumerId = 1L;
            event.eventType = "ARBITRAGE_SELL";
            event.soc_percent = telemetry.State_of_Charge;
            event.recommendedAction = "DISCHARGE";
            event.marketPrice = getCurrentMarketPrice();
            event.incentiveAmount = calculateIncentive(telemetry.State_of_Charge);
            event.gridCellId = telemetry.grid_cell_id;
            event.timestamp = LocalDateTime.now();

            saveAndPublish(event);
        }

        if (telemetry.State_of_Charge != null && telemetry.State_of_Charge < 20.0) {
            FlexibilityEvent event = new FlexibilityEvent();
            event.assetId = telemetry.asset_id;
            event.prosumerId = 1L;
            event.eventType = "BALANCING_UNAVAILABLE";
            event.soc_percent = telemetry.State_of_Charge;
            event.recommendedAction = "UNAVAILABLE";
            event.gridCellId = telemetry.grid_cell_id;
            event.timestamp = LocalDateTime.now();

            saveAndPublish(event);
        }
    }

    private void saveAndPublish(FlexibilityEvent event) {
        event.save(client)
            .subscribe().with(
                eventId -> {
                    String kafkaMessage = String.format(
                        "{\"eventId\":%d,\"assetId\":%d,\"prosumerId\":%d,\"eventType\":\"%s\",\"recommendedAction\":\"%s\",\"timestamp\":\"%s\"}",
                        eventId, event.assetId, event.prosumerId, event.eventType, event.recommendedAction, event.timestamp
                    );
                    flexibilityEmitter.send(kafkaMessage);
                    System.out.printf("FlexibilityEvent created: assetId=%d, eventType=%s, soc=%.2f%%\n",
                        event.assetId, event.eventType, event.soc_percent);
                },
                failure -> System.err.println("Failed to save flexibility event: " + failure.getMessage())
            );
    }

    private Float getCurrentMarketPrice() {
        return 150.0f;
    }

    private Float calculateIncentive(Float soc) {
        return (soc - 90.0f) * 2.0f;
    }
}
