package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class GridBalancingRecommendationResourceIT {

    @InjectMock
    MySQLPool client;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
    }

    // ── CRUD tests ───────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsList() {
        LocalDateTime ts1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime ts2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = recommendationRow(1L, 1006L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", ts1);
        Row row2 = recommendationRow(2L, 1001L, "DISCHARGE", "LISBON-DT", "PORTO-IN", ts2);
        stubQuery("SELECT id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType "
                + "FROM BalancingRecommendation ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/GridBalancingRecommendation")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].assetId", is(1006))
            .body("[0].action", is("REDUCE_CHARGING"))
            .body("[0].fromCell", is("PORTO-IN"))
            .body("[0].toCell", is("PORTO-IN"))
            .body("[1].action", is("DISCHARGE"));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = recommendationRow(1L, 1006L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", ts);
        stubPreparedQuery("SELECT id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType "
                + "FROM BalancingRecommendation WHERE id = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/GridBalancingRecommendation/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("assetId", is(1006))
            .body("action", is("REDUCE_CHARGING"))
            .body("fromCell", is("PORTO-IN"))
            .body("toCell", is("PORTO-IN"));
    }

    @Test
    void getById_returnsNotFound() {
        stubPreparedQuery("SELECT id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType "
                + "FROM BalancingRecommendation WHERE id = ?", rowSetWithRows());

        given()
            .when()
            .get("/GridBalancingRecommendation/99")
            .then()
            .statusCode(404);
    }

    @Test
    void getBySource_returnsFiltered() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row1 = recommendationRow(1L, 1006L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", ts);
        Row row2 = recommendationRow(2L, 1007L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", ts);
        stubPreparedQuery("SELECT id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType "
                + "FROM BalancingRecommendation WHERE fromCell = ? ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/GridBalancingRecommendation/source/PORTO-IN")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].fromCell", is("PORTO-IN"))
            .body("[1].fromCell", is("PORTO-IN"));
    }

    @Test
    void getByMinutes_returnsList() {
        LocalDateTime ts1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime ts2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = recommendationRow(1L, 1006L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", ts1);
        Row row2 = recommendationRow(2L, 1001L, "DISCHARGE", "LISBON-DT", "PORTO-IN", ts2);
        stubPreparedQuery("SELECT id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType "
                + "FROM BalancingRecommendation WHERE createdAt >= ? AND createdAt <= ? ORDER BY createdAt DESC",
                rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/GridBalancingRecommendation/recommendations/20")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].action", is("REDUCE_CHARGING"))
            .body("[1].id", is(2))
            .body("[1].action", is("DISCHARGE"));
    }

    @Test
    void create_withValidData_returns201() {
        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID))
               .thenReturn(42L);
        stubPreparedQuery("INSERT INTO BalancingRecommendation (assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType) VALUES (?,?,?,?,?,?,?,?,?)", insertResult);

        Map<String, Object> body = new HashMap<>();
        body.put("assetId", 1006);
        body.put("action", "REDUCE_CHARGING");
        body.put("fromCell", "PORTO-IN");
        body.put("toCell", "PORTO-IN");
        body.put("createdAt", "2024-01-15T10:30:00");
        body.put("cellContext", "STRESSED");
        body.put("isCharging", true);
        body.put("assetType", "EV_CHARGER");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/GridBalancingRecommendation")
            .then()
            .statusCode(201)
            .header("Location", containsString("/42"));
    }

    @Test
    void update_withExistingId_returns204() {
        RowSet<Row> updateResult = rowSetWithRowCount(1);
        stubPreparedQuery("UPDATE BalancingRecommendation SET assetId = ?, action = ?, "
                + "fromCell = ?, toCell = ?, createdAt = ?, cellContext = ?, socPercent = ?, isCharging = ?, assetType = ? WHERE id = ?", updateResult);

        Map<String, Object> body = new HashMap<>();
        body.put("assetId", 1006);
        body.put("action", "REDUCE_CHARGING");
        body.put("fromCell", "PORTO-IN");
        body.put("toCell", "PORTO-IN");
        body.put("createdAt", "2024-01-15T10:30:00");
        body.put("cellContext", "STRESSED");
        body.put("isCharging", true);
        body.put("assetType", "EV_CHARGER");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/GridBalancingRecommendation/1")
            .then()
            .statusCode(204);
    }

    @Test
    void update_withNonExistingId_returns404() {
        RowSet<Row> updateResult = rowSetWithRowCount(0);
        stubPreparedQuery("UPDATE BalancingRecommendation SET assetId = ?, action = ?, "
                + "fromCell = ?, toCell = ?, createdAt = ?, cellContext = ?, socPercent = ?, isCharging = ?, assetType = ? WHERE id = ?", updateResult);

        Map<String, Object> body = new HashMap<>();
        body.put("assetId", 1006);
        body.put("action", "REDUCE_CHARGING");
        body.put("fromCell", "PORTO-IN");
        body.put("toCell", "PORTO-IN");
        body.put("createdAt", "2024-01-15T10:30:00");
        body.put("cellContext", "STRESSED");
        body.put("isCharging", true);
        body.put("assetType", "EV_CHARGER");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/GridBalancingRecommendation/99")
            .then()
            .statusCode(404);
    }

    @Test
    void delete_withExistingId_returns204() {
        stubPreparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?", rowSetWithRowCount(1));

        given()
            .when()
            .delete("/GridBalancingRecommendation/1")
            .then()
            .statusCode(204);
    }

    @Test
    void delete_withNonExistingId_returns404() {
        stubPreparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?", rowSetWithRowCount(0));

        given()
            .when()
            .delete("/GridBalancingRecommendation/99")
            .then()
            .statusCode(404);
    }

    // ── /metrics tests ───────────────────────────────────────────────────────────

    @Test
    void computeMetrics_singleCell_returns200() {
        Map<String, Object> cell = new HashMap<>();
        cell.put("gridCellId", "PORTO-IN");
        cell.put("maxCapacity", 75.0);

        Map<String, Object> body = new HashMap<>();
        body.put("gridCell", cell);
        body.put("telemetryData", List.of());

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/GridBalancingRecommendation/metrics")
            .then()
            .statusCode(200)
            .body("gridCellId", is("PORTO-IN"));
    }

    @Test
    void computeMetrics_emptyRequest_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .post("/GridBalancingRecommendation/metrics")
            .then()
            .statusCode(400);
    }

    // ── /save tests ──────────────────────────────────────────────────────────────

    @Test
    void saveRecommendations_returns200() {
        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID))
               .thenReturn(10L);
        stubPreparedQuery("INSERT INTO BalancingRecommendation (assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType) VALUES (?,?,?,?,?,?,?,?,?)", insertResult);

        List<Map<String, Object>> body = List.of(Map.of(
                "assetId", 1006,
                "action", "REDUCE_CHARGING",
                "from", "PORTO-IN",
                "to", "PORTO-IN",
                "cellContext", "STRESSED",
                "isCharging", true,
                "assetType", "EV_CHARGER"
        ));

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/GridBalancingRecommendation/save")
            .then()
            .statusCode(200);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

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

    private RowSet<Row> rowSetWithRowCount(int count) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(count);
        return rowSet;
    }

    private Row recommendationRow(Long id, Long assetId, String action,
                                  String fromCell, String toCell, LocalDateTime createdAt) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("assetId")).thenReturn(assetId);
        Mockito.when(row.getString("action")).thenReturn(action);
        Mockito.when(row.getString("fromCell")).thenReturn(fromCell);
        Mockito.when(row.getString("toCell")).thenReturn(toCell);
        Mockito.when(row.getLocalDateTime("createdAt")).thenReturn(createdAt);
        Mockito.when(row.getString("cellContext")).thenReturn("STRESSED");
        Mockito.when(row.getFloat("socPercent")).thenReturn(null);
        Mockito.when(row.getBoolean("isCharging")).thenReturn(false);
        Mockito.when(row.getString("assetType")).thenReturn("EV_CHARGER");
        return row;
    }

    private static final class ListRowIterator implements RowIterator<Row> {
        private final java.util.Iterator<Row> iterator;

        ListRowIterator(List<Row> rows) {
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
