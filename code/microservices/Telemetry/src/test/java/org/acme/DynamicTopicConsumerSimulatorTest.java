package org.acme;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@Testcontainers
@SuppressWarnings("unchecked")
class DynamicTopicConsumerSimulatorTest {

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    private MySQLPool client;
    private PreparedQuery<RowSet<Row>> pq;
    private DynamicTopicConsumer consumer;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(MySQLPool.class);
        pq = Mockito.mock(PreparedQuery.class);
        RowSet<Row> rs = Mockito.mock(RowSet.class);
        Mockito.when(pq.execute(any(Tuple.class))).thenReturn(Uni.createFrom().item(rs));
        Mockito.when(client.preparedQuery(anyString())).thenReturn(pq);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.interrupt();
        }
    }

    @Test
    void batteryMessage_parsedAndPersistedToDatabase() throws Exception {
        String topic = "test-battery-" + UUID.randomUUID();
        String json = "{\"timeStamp\":\"2026-01-15 10:30:00.000\",\"asset_id\":\"560987123\","
                + "\"asset_type\":\"BATTERY\",\"grid_cell_id\":\"AUSTIN-DT\","
                + "\"payload\":{\"soc_percent\":85.5,\"energy_available_kwh\":42.3,"
                + "\"active_power_kw\":5.2,\"max_discharge_power_kw\":10.0,"
                + "\"soh_percent\":95.0,\"connection_status\":\"ONLINE\"}}";

        createTopic(topic);
        startConsumer(topic);
        produce(topic, json);

        ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
        verify(client, timeout(10000)).preparedQuery(contains("INSERT INTO Telemetry"));
        verify(pq, timeout(10000)).execute(tupleCaptor.capture());

        Tuple t = tupleCaptor.getValue();
        assertEquals("560987123", t.getValue(1));
        assertEquals("BATTERY", t.getValue(2));
        assertEquals("AUSTIN-DT", t.getValue(3));
        assertEquals(85.5f, (Float) t.getValue(4), 0.01f);
        assertEquals("ONLINE", t.getValue(9));  // connection_status present in payload
        assertNull(t.getValue(10));              // generation_kw not applicable
        assertNull(t.getValue(14));              // connector_status not applicable
    }

    @Test
    void solarMessage_parsedAndPersistedToDatabase() throws Exception {
        String topic = "test-solar-" + UUID.randomUUID();
        String json = "{\"timeStamp\":\"2026-01-15 10:31:00.000\",\"asset_id\":\"SOLAR-456\","
                + "\"asset_type\":\"SOLAR\",\"grid_cell_id\":\"BERLIN-CE\","
                + "\"payload\":{\"generation_kw\":5.2,\"daily_yield_kwh\":42.1,"
                + "\"ac_voltage_v\":230.0,\"grid_frequency_hz\":50.0}}";

        createTopic(topic);
        startConsumer(topic);
        produce(topic, json);

        ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
        verify(client, timeout(10000)).preparedQuery(contains("INSERT INTO Telemetry"));
        verify(pq, timeout(10000)).execute(tupleCaptor.capture());

        Tuple t = tupleCaptor.getValue();
        assertEquals("SOLAR-456", t.getValue(1));
        assertEquals("SOLAR", t.getValue(2));
        assertEquals("BERLIN-CE", t.getValue(3));
        assertNull(t.getValue(4));               // soc_percent not applicable
        assertNull(t.getValue(9));               // connection_status not applicable
        assertEquals(5.2f, (Float) t.getValue(10), 0.01f);
        assertNull(t.getValue(14));              // connector_status not applicable
    }

    @Test
    void evChargerMessage_parsedAndPersistedToDatabase() throws Exception {
        String topic = "test-ev-" + UUID.randomUUID();
        String json = "{\"timeStamp\":\"2026-01-15 10:32:00.000\",\"asset_id\":\"CHARGER-789\","
                + "\"asset_type\":\"EV_CHARGER\",\"grid_cell_id\":\"LONDON-SE\","
                + "\"payload\":{\"connector_status\":\"CHARGING\",\"charging_power_kw\":11.5,"
                + "\"session_energy_kwh\":8.3,\"ev_soc_percent\":72.0}}";

        createTopic(topic);
        startConsumer(topic);
        produce(topic, json);

        ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
        verify(client, timeout(10000)).preparedQuery(contains("INSERT INTO Telemetry"));
        verify(pq, timeout(10000)).execute(tupleCaptor.capture());

        Tuple t = tupleCaptor.getValue();
        assertEquals("CHARGER-789", t.getValue(1));
        assertEquals("EV_CHARGER", t.getValue(2));
        assertEquals("LONDON-SE", t.getValue(3));
        assertNull(t.getValue(4));               // soc_percent not applicable
        assertNull(t.getValue(9));               // connection_status not applicable
        assertEquals("CHARGING", t.getValue(14));
        assertEquals(11.5f, (Float) t.getValue(15), 0.01f);
    }

    private void createTopic(String topic) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1)))
                    .all().get(10, TimeUnit.SECONDS);
        }
    }

    private void startConsumer(String topic) throws InterruptedException {
        consumer = new DynamicTopicConsumer(topic, kafka.getBootstrapServers(), client);
        consumer.setDaemon(true);
        consumer.start();
        Thread.sleep(3000);
    }

    private void produce(String topic, String json) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), json))
                    .get(10, TimeUnit.SECONDS);
        }
    }
}
