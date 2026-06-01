package org.acme;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.acme.entities.AverageSoC;
import org.acme.entities.ConsumedEnergyByProsumer;
import org.acme.entities.EnergyDischargedByZone;
import org.acme.entities.GeneratedEnergyByProsumer;
import org.acme.services.AnalyticsCalculationService;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
@QuarkusTestResource(EnergyAnalyticsKafkaTest.KafkaResource.class)
class EnergyAnalyticsKafkaTest {

    @InjectMock
    MySQLPool client;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    AnalyticsCalculationService analyticsService;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
    }

    @AfterAll
    static void resetConnector() {
        InMemoryConnector.clear();
    }

    @Test
    void emitAnalytics_publishesKafkaMessages() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);

        List<GeneratedEnergyByProsumer> generated = Collections.singletonList(
            new GeneratedEnergyByProsumer(null, 101L, 50.0, 1, timestamp, "LAST_30_MIN")
        );
        List<ConsumedEnergyByProsumer> consumed = Collections.singletonList(
            new ConsumedEnergyByProsumer(null, 202L, 20.0, 1, timestamp, "LAST_30_MIN")
        );
        List<EnergyDischargedByZone> discharged = Collections.singletonList(
            new EnergyDischargedByZone(null, "GRID_A", 10.0, 1, timestamp, "LAST_30_MIN")
        );
        AverageSoC avgSoC = new AverageSoC(null, 92.0, 1, timestamp, "LAST_30_MIN");

        stubInsert("INSERT INTO GeneratedEnergyByProsumer(prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)", 1L);
        stubInsert("INSERT INTO ConsumedEnergyByProsumer(prosumerId, totalEnergyConsumedKw, evChargerCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)", 2L);
        stubInsert("INSERT INTO EnergyDischargedByZone(gridCellId, totalEnergyDischargedKw, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)", 3L);
        stubInsert("INSERT INTO AverageSoC(averageSocPercent, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?)", 4L);

        analyticsService.emitAnalytics(generated, consumed, discharged, avgSoC)
            .await().indefinitely();

        assertSingleMessage("energy-discharged-zone", "GRID_A", "totalDischarge", 10.0, "batteryCount", 1, "timestamp");
        assertSingleMessage("energy-generated-prosumer", "101", "totalGeneration", 50.0, "assetCount", 1, "timestamp");
        assertSingleMessage("energy-consumed-prosumer", "202", "totalConsumption", 20.0, "chargerCount", 1, "timestamp");
        assertSingleMessage("average-soc", "system", "averageSoC", 92.0, "batteryCount", 1, "timestamp");
    }

    private void assertSingleMessage(String channel, String expectedKey, String numericField, double expectedNumeric,
                                     String countField, int expectedCount, String timestampField) {
        InMemorySink<String> sink = connector.sink(channel);
        Assertions.assertEquals(1, sink.received().size(), channel + " should receive one message");

        var message = sink.received().get(0);
        JSONObject payload = new JSONObject(message.getPayload());
        Assertions.assertEquals(expectedNumeric, payload.getDouble(numericField), 0.0001, channel + " payload value mismatch");
        Assertions.assertEquals(expectedCount, payload.getInt(countField), channel + " count mismatch");
        Assertions.assertTrue(payload.has(timestampField), channel + " should contain timestamp");

        OutgoingKafkaRecordMetadata<String> metadata = message.getMetadata(OutgoingKafkaRecordMetadata.class)
            .orElseThrow(() -> new AssertionError(channel + " should include Kafka metadata"));
        Assertions.assertEquals(expectedKey, metadata.getKey(), channel + " Kafka key mismatch");
    }

    private void stubInsert(String sql, Long insertedId) {
        RowSet<Row> insertResult = Mockito.mock(RowSet.class);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(insertedId);

        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(io.smallrye.mutiny.Uni.createFrom().item(insertResult));
        Mockito.when(client.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    public static class KafkaResource implements QuarkusTestResourceLifecycleManager {
        @Override
        public Map<String, String> start() {
            Map<String, String> env = new HashMap<>();
            env.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("energy-discharged-zone"));
            env.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("energy-generated-prosumer"));
            env.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("energy-consumed-prosumer"));
            env.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("average-soc"));
            return env;
        }

        @Override
        public void stop() {
            InMemoryConnector.clear();
        }
    }
}
