package org.acme;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.acme.dto.BatchEvaluateRequest;
import org.acme.dto.BatchEvaluateResponse;
import org.acme.dto.BatteryAssetDTO;
import org.acme.dto.TelemetryDTO;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@QuarkusTest
@QuarkusTestResource(FlexibilityEventKafkaTest.KafkaResource.class)
class FlexibilityEventKafkaTest {

    @InjectMock
    MySQLPool client;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    FlexibilityEventResource resource;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
        connector.sink("flexibility-offers").received().clear();
    }

    @AfterAll
    static void resetConnector() {
        InMemoryConnector.clear();
    }

    @Test
    void evaluateBatch_doesNotPublishToKafka() {
        BatchEvaluateRequest request = new BatchEvaluateRequest();
        request.batteryAssets = List.of(new BatteryAssetDTO(732L, 1L));
        request.telemetryList = List.of(telemetry(1L, 95.0f, "GRID_A"));
        InMemorySink<String> sink = connector.sink("flexibility-offers");
        int before = sink.received().size();

        Response response = resource.evaluateBatch(request);
        Assertions.assertEquals(200, response.getStatus());
        Assertions.assertEquals(before, sink.received().size(), "flexibility-offers should not receive a message on evaluate");
    }

    @Test
    void emitFlexibilityOffers_publishesKafkaMessage() {
        FlexibilityEvent event = new FlexibilityEvent();
        event.assetId = 1L;
        event.prosumerId = 732L;
        event.eventType = "ARBITRAGE_SELL";
        event.recommendedAction = "DISCHARGE";
        event.timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);

        stubInsert("INSERT INTO FlexibilityEvent(assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp) VALUES (?,?,?,?,?,?,?,?,?)", 123L);
        InMemorySink<String> sink = connector.sink("flexibility-offers");
        int before = sink.received().size();

        Response response = resource.emitFlexibilityOffers(new BatchEvaluateResponse(List.of(event))).await().indefinitely();
        Assertions.assertEquals(200, response.getStatus());
        Assertions.assertEquals(before + 1, sink.received().size(), "flexibility-offers should receive one new message");

        JSONObject payload = new JSONObject(sink.received().get(sink.received().size() - 1).getPayload());
        Assertions.assertEquals(123, payload.getInt("eventId"));
        Assertions.assertEquals(1, payload.getInt("assetId"));
        Assertions.assertEquals(732, payload.getInt("prosumerId"));
        Assertions.assertEquals("ARBITRAGE_SELL", payload.getString("eventType"));
        Assertions.assertEquals("DISCHARGE", payload.getString("recommendedAction"));
        Assertions.assertTrue(payload.has("timestamp"));
    }

    private void stubInsert(String sql, Long insertedId) {
        RowSet<Row> insertResult = Mockito.mock(RowSet.class);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(insertedId);

        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(insertResult));
        Mockito.when(client.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private TelemetryDTO telemetry(Long assetId, Float soc, String gridCellId) {
        TelemetryDTO telemetry = new TelemetryDTO();
        telemetry.asset_id = assetId;
        telemetry.State_of_Charge = soc;
        telemetry.grid_cell_id = gridCellId;
        telemetry.asset_type = "BATTERY";
        telemetry.timeStamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        return telemetry;
    }

    public static class KafkaResource implements QuarkusTestResourceLifecycleManager {
        @Override
        public Map<String, String> start() {
            return new HashMap<>(InMemoryConnector.switchOutgoingChannelsToInMemory("flexibility-offers"));
        }

        @Override
        public void stop() {
            InMemoryConnector.clear();
        }
    }
}