package org.acme.consumers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import org.json.*;

import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Tuple;

public class GridCellEventProcessor extends Thread {
    private String kafka_servers;
    private MySQLPool client;

    public GridCellEventProcessor(String kafka_servers, MySQLPool client) {
        this.kafka_servers = kafka_servers;
        this.client = client;
    }

    public void run() {
        try {
            Properties properties = new Properties();
            properties.put("bootstrap.servers", kafka_servers);
            properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("group.id", "grid-balancing-gridcell-group");

            try (Consumer<String, String> consumer = new KafkaConsumer<>(properties)) {
                consumer.subscribe(Arrays.asList("GridCell-Created", "GridCell-Updated", "GridCell-Deleted"));

                System.out.println("GridCellEventProcessor: Subscribed to GridCell events");

                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        System.out.printf("GridBalancing - Received GridCell event: topic = %s, key = %s\n",
                            record.topic(), record.key());

                        processGridCellEvent(record.topic(), record.value());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Exception in GridCellEventProcessor: " + e);
            e.printStackTrace();
        }
    }

    private void processGridCellEvent(String topic, String jsonString) {
        JSONObject obj = new JSONObject(jsonString);
        String gridCellId = obj.getString("gridCellId");

        try {
            if (topic.equals("GridCell-Deleted")) {
                client.preparedQuery("DELETE FROM GridCell WHERE gridCellId = ?")
                    .execute(Tuple.of(gridCellId))
                    .await().indefinitely();
                System.out.printf("GridCell deleted from local DB: %s\n", gridCellId);
            } else if (topic.equals("GridCell-Created")) {
                Long utilityOperatorId = obj.getLong("utilityOperatorId");
                Double maxCapacity = obj.getDouble("maxCapacity");
                String geographicBoundaries = obj.getString("geographicBoundaries");

                client.preparedQuery("INSERT INTO GridCell (gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE utilityOperatorId=?, maxCapacity=?, geographicBoundaries=?")
                    .execute(Tuple.of(gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries, utilityOperatorId, maxCapacity, geographicBoundaries))
                    .await().indefinitely();
                System.out.printf("GridCell created in local DB: %s (capacity: %.2f)\n", gridCellId, maxCapacity);
            } else if (topic.equals("GridCell-Updated")) {
                Long utilityOperatorId = obj.getLong("utilityOperatorId");
                Double maxCapacity = obj.getDouble("maxCapacity");
                String geographicBoundaries = obj.getString("geographicBoundaries");

                client.preparedQuery("UPDATE GridCell SET utilityOperatorId=?, maxCapacity=?, geographicBoundaries=? WHERE gridCellId=?")
                    .execute(Tuple.of(utilityOperatorId, maxCapacity, geographicBoundaries, gridCellId))
                    .await().indefinitely();
                System.out.printf("GridCell updated in local DB: %s (capacity: %.2f)\n", gridCellId, maxCapacity);
            }
        } catch (Exception e) {
            System.err.println("Error processing GridCell event: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
