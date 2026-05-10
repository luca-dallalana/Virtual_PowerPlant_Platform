package org.acme;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.acme.dto.GridCellDTO;
import org.acme.dto.TelemetryDTO;
import org.acme.services.GridBalancingRecommendationService;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
@QuarkusTestResource(GridBalancingRecommendationKafkaTest.KafkaResource.class)
class GridBalancingRecommendationKafkaTest {

    @InjectMock
    MySQLPool client;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    GridBalancingRecommendationService recommendationService;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
    }

    @AfterAll
    static void resetConnector() {
        InMemoryConnector.clear();
    }

    @Test
    void calculateRecommendationsFromEvents_publishesKafkaMessage() {
        List<GridCellDTO> gridCells = List.of(
                gridCell("GRID_A", 100.0),
                gridCell("GRID_B", 100.0)
        );
        List<TelemetryDTO> telemetry = List.of(
                evTelemetry(1L, "GRID_A", 95.0f),
                evTelemetry(2L, "GRID_B", 50.0f)
        );

        stubInsert("INSERT INTO BalancingRecommendation(sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?)", 11L);

        recommendationService.calculateRecommendationsFromEvents(telemetry, gridCells)
                .await().indefinitely();

        InMemorySink<String> sink = connector.sink("grid-balancing-recommendation");
        Assertions.assertEquals(1, sink.received().size(), "grid-balancing-recommendation should receive one message");

        var message = sink.received().get(0);
        JSONObject payload = new JSONObject(message.getPayload());
        Assertions.assertEquals("GRID_A", payload.getString("sourceGridCellId"));
        Assertions.assertEquals("GRID_B", payload.getString("targetGridCellId"));
        Assertions.assertEquals(5.0, payload.getDouble("overloadKw"), 0.0001);
        Assertions.assertEquals(5.0, payload.getDouble("transferableKw"), 0.0001);
        Assertions.assertEquals(0.9, payload.getDouble("thresholdPercent"), 0.0001);
        Assertions.assertTrue(payload.has("timestamp"));

        OutgoingKafkaRecordMetadata<String> metadata = message.getMetadata(OutgoingKafkaRecordMetadata.class)
                .orElseThrow(() -> new AssertionError("Kafka metadata should be present"));
        Assertions.assertEquals("GRID_A", metadata.getKey());
    }

    private void stubInsert(String sql, Long insertedId) {
        RowSet<Row> insertResult = Mockito.mock(RowSet.class);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(insertedId);

        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(insertResult));
        Mockito.when(client.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private GridCellDTO gridCell(String gridCellId, Double maxCapacity) {
        GridCellDTO gridCell = new GridCellDTO();
        gridCell.gridCellId = gridCellId;
        gridCell.maxCapacity = maxCapacity;
        return gridCell;
    }

    private TelemetryDTO evTelemetry(Long assetId, String gridCellId, Float chargingRate) {
        TelemetryDTO telemetry = new TelemetryDTO();
        telemetry.asset_id = assetId;
        telemetry.grid_cell_id = gridCellId;
        telemetry.Charging_Rate = chargingRate;
        telemetry.timeStamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        telemetry.asset_type = "EV_CHARGER";
        return telemetry;
    }

    public static class KafkaResource implements QuarkusTestResourceLifecycleManager {
        @Override
        public Map<String, String> start() {
            return new HashMap<>(InMemoryConnector.switchOutgoingChannelsToInMemory("grid-balancing-recommendation"));
        }

        @Override
        public void stop() {
            InMemoryConnector.clear();
        }
    }
}