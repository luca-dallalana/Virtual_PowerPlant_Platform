package org.acme.consumers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import org.json.*;

import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Tuple;

public class AssetLinkEventProcessor extends Thread {
    private String kafka_servers;
    private MySQLPool client;

    public AssetLinkEventProcessor(String kafka_servers, MySQLPool client) {
        this.kafka_servers = kafka_servers;
        this.client = client;
    }

    public void run() {
        try {
            Properties properties = new Properties();
            properties.put("bootstrap.servers", kafka_servers);
            properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("group.id", "energy-analytics-assetlink-group");

            try (Consumer<String, String> consumer = new KafkaConsumer<>(properties)) {
                consumer.subscribe(Collections.singletonList("AssetLink-Events"));

                System.out.println("AssetLinkEventProcessor: Subscribed to AssetLink-Events");

                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        System.out.printf("EnergyAnalytics - Received AssetLink event: key = %s\n",
                            record.key());

                        processAssetLinkEvent(record.value());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Exception in AssetLinkEventProcessor (EnergyAnalytics): " + e);
            e.printStackTrace();
        }
    }

    private void processAssetLinkEvent(String jsonString) {
        JSONObject obj = new JSONObject(jsonString);
        Long assetLinkId = obj.getLong("assetLinkId");
        String eventType = obj.getString("eventType");

        try {
            if ("DELETED".equals(eventType)) {
                client.preparedQuery("DELETE FROM AssetLink WHERE assetLinkId = ?")
                    .execute(Tuple.of(assetLinkId))
                    .await().indefinitely();
                System.out.printf("AssetLink deleted from local DB: %d\n", assetLinkId);
            } else if ("CREATED".equals(eventType)) {
                Long assetId = obj.getLong("assetId");
                Long prosumerId = obj.getLong("prosumerId");
                Long utilityOperatorId = obj.getLong("utilityOperatorId");
                String gridCellId = obj.getString("gridCellId");
                String status = obj.getString("status");

                client.preparedQuery("INSERT INTO AssetLink (assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE assetId=?, prosumerId=?, utilityOperatorId=?, gridCellId=?, status=?")
                    .execute(Tuple.of(assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status, assetId, prosumerId, utilityOperatorId, gridCellId, status))
                    .await().indefinitely();
                System.out.printf("AssetLink created in local DB: assetId=%d, prosumerId=%d\n", assetId, prosumerId);
            } else if ("UPDATED".equals(eventType)) {
                Long assetId = obj.getLong("assetId");
                Long prosumerId = obj.getLong("prosumerId");
                Long utilityOperatorId = obj.getLong("utilityOperatorId");
                String gridCellId = obj.getString("gridCellId");
                String status = obj.getString("status");

                client.preparedQuery("UPDATE AssetLink SET assetId=?, prosumerId=?, utilityOperatorId=?, gridCellId=?, status=? WHERE assetLinkId=?")
                    .execute(Tuple.of(assetId, prosumerId, utilityOperatorId, gridCellId, status, assetLinkId))
                    .await().indefinitely();
                System.out.printf("AssetLink updated in local DB: assetId=%d, prosumerId=%d\n", assetId, prosumerId);
            }
        } catch (Exception e) {
            System.err.println("Error processing AssetLink event: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
