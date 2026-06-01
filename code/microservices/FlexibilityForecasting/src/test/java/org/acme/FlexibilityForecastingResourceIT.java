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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class FlexibilityForecastingResourceIT {

    @InjectMock
    MySQLPool client;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
    }

    // ── GET /history ──────────────────────────────────────────────────────────

    @Test
    void getHistory_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = forecastingResultRow(1L, "Forecast A", "2024-01-15T10:00", "2024-01-15T10:30", 5, 3, timestamp1);
        Row row2 = forecastingResultRow(2L, "Forecast B", "2024-01-15T11:00", "2024-01-15T11:30", 8, 4, timestamp2);
        stubQuery("SELECT * FROM ForecastingResult ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/FlexibilityForecasting/history")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].forecastResult", is("Forecast A"))
            .body("[0].flexibilityEventsCount", is(5));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = forecastingResultRow(1L, "Forecast A", "2024-01-15T10:00", "2024-01-15T10:30", 5, 3, timestamp);
        stubPreparedQuery("SELECT * FROM ForecastingResult WHERE id = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/FlexibilityForecasting/history/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("forecastResult", is("Forecast A"))
            .body("flexibilityEventsCount", is(5));
    }

    @Test
    void getById_returnsNotFound() {
        stubPreparedQuery("SELECT * FROM ForecastingResult WHERE id = ?", rowSetWithRows());
        given()
            .when()
            .get("/FlexibilityForecasting/history/99")
            .then()
            .statusCode(404);
    }

    // ── POST /evaluate-correlation ────────────────────────────────────────────

    @Test
    void evaluateCorrelation_emptyInputs_returnsZeroStats() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"flexibilityLogs\":[],\"gridBalancingLogs\":[],\"solarAssets\":[],\"solarTelemetry\":[]}")
            .when()
            .post("/FlexibilityForecasting/evaluate-correlation")
            .then()
            .statusCode(200)
            .body("totalFlexibilityEvents", is(0))
            .body("solarAssetCount", is(0));
    }

    @Test
    void evaluateCorrelation_withEvents_returnsStats() {
        Map<String, Object> event = new HashMap<>();
        event.put("assetId", 100);
        event.put("prosumerId", 10);
        event.put("eventType", "ARBITRAGE_SELL");
        event.put("recommendedAction", "DISCHARGE");
        event.put("soc_percent", 92.0);
        event.put("gridCellId", "GRID_A");
        event.put("timestamp", "2024-01-15T10:30:00");

        Map<String, Object> body = new HashMap<>();
        body.put("flexibilityLogs", Collections.singletonList(event));
        body.put("gridBalancingLogs", Collections.emptyList());
        body.put("solarAssets", Collections.emptyList());
        body.put("solarTelemetry", Collections.emptyList());

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/FlexibilityForecasting/evaluate-correlation")
            .then()
            .statusCode(200)
            .body("totalFlexibilityEvents", is(1))
            .body("correlatedEvents", hasSize(1));
    }

    // ── POST /build-prompt ───────────────────────────────────────────────────

    @Test
    void buildPrompt_returnsPromptString() {
        Map<String, Object> correlation = new HashMap<>();
        correlation.put("flexibilityLogs", Collections.emptyList());
        correlation.put("gridBalancingLogs", Collections.emptyList());
        correlation.put("solarAssets", Collections.emptyList());
        correlation.put("solarTelemetry", Collections.emptyList());
        correlation.put("correlatedEvents", Collections.emptyList());
        correlation.put("solarGenerationByGridCell", Collections.emptyMap());
        correlation.put("totalFlexibilityEvents", 0);
        correlation.put("totalGridBalancingLogs", 0);
        correlation.put("correlatedOutcomes", 0);
        correlation.put("successRate", 0.0);
        correlation.put("solarAssetCount", 0);
        correlation.put("avgCurrentGenerationKw", 0.0);
        correlation.put("totalDailyGenerationKwh", 0.0);

        given()
            .contentType(ContentType.JSON)
            .body(correlation)
            .when()
            .post("/FlexibilityForecasting/build-prompt")
            .then()
            .statusCode(200)
            .body("prompt", notNullValue());
    }

    // ── POST /forecast ────────────────────────────────────────────────────────

    @Test
    void persistForecast_returnsId() {
        RowSet<Row> insertResult = Mockito.mock(RowSet.class);
        Mockito.when(insertResult.property(Mockito.any())).thenReturn(99L);
        stubPreparedInsert("INSERT INTO ForecastingResult(forecastResult, windowStart, windowEnd, flexibilityEventsCount, gridBalancingCount, createdAt) VALUES (?,?,?,?,?,?)", insertResult);

        given()
            .contentType(ContentType.JSON)
            .body("{\"forecastResult\":\"{\\\"summary\\\":\\\"all good\\\"}\"}")
            .when()
            .post("/FlexibilityForecasting/forecast")
            .then()
            .statusCode(200)
            .body("forecastId", is(99));
    }

    // ── DELETE /history/{id} ─────────────────────────────────────────────────

    @Test
    void delete_withExistingId_returns204() {
        stubPreparedQuery("DELETE FROM ForecastingResult WHERE id = ?", rowSetWithRowCount(1));
        given()
            .when()
            .delete("/FlexibilityForecasting/history/1")
            .then()
            .statusCode(204);
    }

    @Test
    void delete_withNonExistingId_returns404() {
        stubPreparedQuery("DELETE FROM ForecastingResult WHERE id = ?", rowSetWithRowCount(0));
        given()
            .when()
            .delete("/FlexibilityForecasting/history/99")
            .then()
            .statusCode(404);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> pq = Mockito.mock(PreparedQuery.class);
        Mockito.when(pq.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery(sql)).thenReturn(pq);
    }

    private void stubPreparedInsert(String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> pq = Mockito.mock(PreparedQuery.class);
        Mockito.when(pq.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery(sql)).thenReturn(pq);
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

    private Row forecastingResultRow(Long id, String forecastResult, String windowStart, String windowEnd,
                                     Integer flexibilityEventsCount, Integer gridBalancingCount, LocalDateTime createdAt) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("forecastResult")).thenReturn(forecastResult);
        Mockito.when(row.getString("windowStart")).thenReturn(windowStart);
        Mockito.when(row.getString("windowEnd")).thenReturn(windowEnd);
        Mockito.when(row.getInteger("flexibilityEventsCount")).thenReturn(flexibilityEventsCount);
        Mockito.when(row.getInteger("gridBalancingCount")).thenReturn(gridBalancingCount);
        Mockito.when(row.getLocalDateTime("createdAt")).thenReturn(createdAt);
        return row;
    }

    private static final class ListRowIterator implements RowIterator<Row> {
        private final java.util.Iterator<Row> iterator;

        private ListRowIterator(List<Row> rows) {
            this.iterator = rows.iterator();
        }

        @Override
        public boolean hasNext() { return iterator.hasNext(); }

        @Override
        public Row next() { return iterator.next(); }
    }
}
