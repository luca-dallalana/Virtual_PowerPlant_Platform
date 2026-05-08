package org.acme;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(TelemetryKafkaMySqlIT.TestResources.class)
class TelemetryKafkaMySqlIT {

    @Test
    void consumeKafkaAndStoreTelemetry() throws Exception {
        given()
            .contentType(ContentType.JSON)
            .body("{\"TopicName\":\"Telemetry-Data\"}")
            .when()
            .post("/Telemetry/Consume")
            .then()
            .statusCode(200)
            .body(is("New worker started"));

        String payload = "{"
            + "\"timeStamp\":\"2024-01-10 12:30:00\","
            + "\"asset_type\":\"BATTERY\","
            + "\"asset_id\":\"1001\","
            + "\"grid_cell_id\":\"CELL-1\","
            + "\"payload\":{"
            + "\"soc_percent\":70.5,"
            + "\"energy_available_kwh\":12.3,"
            + "\"active_power_kw\":3.2,"
            + "\"max_discharge_power_kw\":5.0,"
            + "\"soh_percent\":99.0,"
            + "\"connection_status\":\"CONNECTED\""
            + "}"
            + "}";

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("Telemetry-Data", "key", payload)).get();
        }

        boolean stored = false;
        for (int attempt = 0; attempt < 15; attempt++) {
            try {
                given()
                    .when()
                    .get("/Telemetry")
                    .then()
                    .statusCode(200)
                    .body("", hasSize(1))
                    .body("[0].asset_id", is(1001))
                    .body("[0].asset_type", is("BATTERY"));
                stored = true;
                break;
            } catch (AssertionError ex) {
                Thread.sleep(200);
            }
        }

        if (!stored) {
            throw new AssertionError("Telemetry message was not stored in time");
        }
    }

    static String kafkaBootstrapServers;

    public static class TestResources implements QuarkusTestResourceLifecycleManager {
        private KafkaContainer kafka;
        private MySQLContainer<?> mysql;

        @Override
        public Map<String, String> start() {
            kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
            kafka.start();

            mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.3.0"))
                .withDatabaseName("telemetry")
                .withUsername("teste")
                .withPassword("testeteste");
            mysql.start();

            kafkaBootstrapServers = kafka.getBootstrapServers();

            Map<String, String> config = new HashMap<>();
            config.put("kafka.bootstrap.servers", kafkaBootstrapServers);
            config.put("quarkus.datasource.db-kind", "mysql");
            config.put("quarkus.datasource.username", mysql.getUsername());
            config.put("quarkus.datasource.password", mysql.getPassword());
            config.put("quarkus.datasource.reactive.url", String.format(
                "mysql://%s:%d/%s",
                mysql.getHost(),
                mysql.getMappedPort(3306),
                mysql.getDatabaseName()
            ));
            config.put("myapp.schema.create", "true");
            return config;
        }

        @Override
        public void stop() {
            if (kafka != null) {
                kafka.stop();
            }
            if (mysql != null) {
                mysql.stop();
            }
        }
    }
}
