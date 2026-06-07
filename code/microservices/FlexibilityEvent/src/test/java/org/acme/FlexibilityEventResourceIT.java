package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class FlexibilityEventResourceIT {

    @InjectMock
    MySQLPool client;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
    }

    @Test
    void getFlexibilityEvents_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = flexibilityEventRow(1L, 1L, 1L, "ARBITRAGE_SELL", 95.0f, 92.5f, "DISCHARGE", "HIGH", "GRID_A", timestamp1);
        Row row2 = flexibilityEventRow(2L, 2L, 1L, "BALANCING_UNAVAILABLE", 15.0f, 80.0f, "UNAVAILABLE", null, "GRID_B", timestamp2);
        stubQuery(
            "SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp " +
            "FROM FlexibilityEvent ORDER BY timestamp DESC",
            rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/FlexibilityEvent")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].assetId", is(1))
            .body("[0].prosumerId", is(1))
            .body("[0].eventType", is("ARBITRAGE_SELL"))
            .body("[0].soc_percent", is(95.0f))
            .body("[0].recommendedAction", is("DISCHARGE"))
            .body("[0].marketPriceLevel", is("HIGH"))
            .body("[0].gridCellId", is("GRID_A"));
    }

    @Test
    void getFlexibilityEventById_returnsEntity() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = flexibilityEventRow(1L, 5L, 2L, "ARBITRAGE_SELL", 95.0f, 92.5f, "DISCHARGE", "HIGH", "GRID_A", timestamp);
        stubPreparedQuery(
            "SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp " +
            "FROM FlexibilityEvent WHERE id = ?",
            rowSetWithRows(row));

        given()
            .when()
            .get("/FlexibilityEvent/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("assetId", is(5))
            .body("prosumerId", is(2))
            .body("eventType", is("ARBITRAGE_SELL"))
            .body("soc_percent", is(95.0f))
            .body("recommendedAction", is("DISCHARGE"))
            .body("marketPriceLevel", is("HIGH"))
            .body("gridCellId", is("GRID_A"));
    }

    @Test
    void getFlexibilityEventById_returnsNotFound() {
        stubPreparedQuery(
            "SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp " +
            "FROM FlexibilityEvent WHERE id = ?",
            rowSetWithRows());

        given()
            .when()
            .get("/FlexibilityEvent/99")
            .then()
            .statusCode(404);
    }

    @Test
    void getFlexibilityEventsByAssetId_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = flexibilityEventRow(1L, 5L, 1L, "ARBITRAGE_SELL", 95.0f, 92.5f, "DISCHARGE", "HIGH", "GRID_A", timestamp1);
        Row row2 = flexibilityEventRow(2L, 5L, 2L, "BALANCING_UNAVAILABLE", 15.0f, 75.0f, "UNAVAILABLE", null, "GRID_B", timestamp2);
        stubPreparedQuery(
            "SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp " +
            "FROM FlexibilityEvent WHERE assetId = ? ORDER BY timestamp DESC",
            rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/FlexibilityEvent/asset/5")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].assetId", is(5))
            .body("[1].assetId", is(5));
    }

    @Test
    void getFlexibilityEventsByEventType_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = flexibilityEventRow(1L, 1L, 1L, "ARBITRAGE_SELL", 95.0f, 92.5f, "DISCHARGE", "HIGH", "GRID_A", timestamp1);
        Row row2 = flexibilityEventRow(2L, 2L, 1L, "ARBITRAGE_SELL", 92.0f, 88.0f, "DISCHARGE", "HIGH", "GRID_B", timestamp2);
        stubPreparedQuery(
            "SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp " +
            "FROM FlexibilityEvent WHERE eventType = ? ORDER BY timestamp DESC",
            rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/FlexibilityEvent/type/ARBITRAGE_SELL")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].eventType", is("ARBITRAGE_SELL"))
            .body("[1].eventType", is("ARBITRAGE_SELL"));
    }

    @Test
    void getLogsByMinutes_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = flexibilityEventRow(1L, 1L, 1L, "ARBITRAGE_SELL", 95.0f, 92.5f, "DISCHARGE", "HIGH", "GRID_A", timestamp1);
        Row row2 = flexibilityEventRow(2L, 2L, 1L, "BALANCING_UNAVAILABLE", 15.0f, 75.0f, "UNAVAILABLE", null, "GRID_B", timestamp2);
        stubPreparedQuery(
            "SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp " +
            "FROM FlexibilityEvent WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC",
            rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/FlexibilityEvent/logs/20")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].eventType", is("ARBITRAGE_SELL"))
            .body("[1].id", is(2))
            .body("[1].eventType", is("BALANCING_UNAVAILABLE"));
    }

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private RowSet<Row> rowSetWithRows(Row... rows) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        io.vertx.mutiny.sqlclient.RowIterator<Row> iterator =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Arrays.asList(rows)));
        Mockito.when(rowSet.iterator()).thenReturn(iterator);
        return rowSet;
    }

    private Row flexibilityEventRow(Long id, Long assetId, Long prosumerId, String eventType,
                                    Float soc_percent, Float soh_percent, String recommendedAction,
                                    String marketPriceLevel, String gridCellId, LocalDateTime timestamp) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("assetId")).thenReturn(assetId);
        Mockito.when(row.getLong("prosumerId")).thenReturn(prosumerId);
        Mockito.when(row.getString("eventType")).thenReturn(eventType);
        Mockito.when(row.getFloat("soc_percent")).thenReturn(soc_percent);
        Mockito.when(row.getFloat("soh_percent")).thenReturn(soh_percent);
        Mockito.when(row.getString("recommendedAction")).thenReturn(recommendedAction);
        Mockito.when(row.getString("marketPriceLevel")).thenReturn(marketPriceLevel);
        Mockito.when(row.getString("gridCellId")).thenReturn(gridCellId);
        Mockito.when(row.getLocalDateTime("timestamp")).thenReturn(timestamp);
        return row;
    }

    private static final class ListRowIterator implements RowIterator<Row> {
        private final java.util.Iterator<Row> iterator;

        private ListRowIterator(List<Row> rows) {
            this.iterator = rows.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Row next() {
            return iterator.next();
        }
    }
}
