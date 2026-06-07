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
import java.util.List;

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
        LocalDateTime ts1 = LocalDateTime.of(2026, 6, 7, 10, 0);
        LocalDateTime ts2 = LocalDateTime.of(2026, 6, 7, 11, 0);
        Row row1 = forecastRow(1L, 83.3f, "POSITIVE", 6, "[1,2,3]", ts1);
        Row row2 = forecastRow(2L, 50.0f, "NEUTRAL",  2, "[4,5]",   ts2);
        stubQuery("SELECT * FROM ForecastingResult ORDER BY createdAt DESC", rowSetOf(row1, row2));

        given()
            .when()
            .get("/FlexibilityForecasting/history")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].successRate", is(83.3f))
            .body("[0].dominantSentiment", is("POSITIVE"))
            .body("[0].totalEventsAnalyzed", is(6));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime ts = LocalDateTime.of(2026, 6, 7, 10, 0);
        Row row = forecastRow(1L, 83.3f, "POSITIVE", 6, "[1,2,3]", ts);
        stubPreparedQuery("SELECT * FROM ForecastingResult WHERE id = ?", rowSetOf(row));

        given()
            .when()
            .get("/FlexibilityForecasting/history/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("successRate", is(83.3f))
            .body("dominantSentiment", is("POSITIVE"))
            .body("totalEventsAnalyzed", is(6))
            .body("analyzedEventIds", is("[1,2,3]"));
    }

    @Test
    void getById_returnsNotFound() {
        stubPreparedQuery("SELECT * FROM ForecastingResult WHERE id = ?", rowSetOf());
        given()
            .when()
            .get("/FlexibilityForecasting/history/99")
            .then()
            .statusCode(404);
    }

    // ── POST /build-prompt ───────────────────────────────────────────────────

    @Test
    void buildPrompt_returnsPromptString() {
        given()
            .contentType(ContentType.JSON)
            .body("{" +
                "\"eventId\":1," +
                "\"assetId\":1001," +
                "\"eventType\":\"ARBITRAGE_SELL\"," +
                "\"recommendedAction\":\"DISCHARGE\"," +
                "\"socAtEventTime\":95.2," +
                "\"sohAtEventTime\":92.5," +
                "\"marketPriceLevel\":\"HIGH\"," +
                "\"gridCellId\":\"LISBON-DT\"," +
                "\"currentSoc\":90.9," +
                "\"currentOutputKw\":5.5," +
                "\"currentStatus\":\"ONLINE\"" +
                "}")
            .when()
            .post("/FlexibilityForecasting/build-prompt")
            .then()
            .statusCode(200)
            .body("prompt", notNullValue())
            .body("prompt", org.hamcrest.Matchers.containsString("1001"))
            .body("prompt", org.hamcrest.Matchers.containsString("DISCHARGE"))
            .body("prompt", org.hamcrest.Matchers.containsString("LISBON-DT"));
    }

    // ── POST /forecast ────────────────────────────────────────────────────────

    @Test
    void persistForecast_returnsId() {
        RowSet<Row> insertResult = Mockito.mock(RowSet.class);
        Mockito.when(insertResult.property(Mockito.any())).thenReturn(99L);
        stubPreparedInsert(
            "INSERT INTO ForecastingResult(successRate, dominantSentiment, totalEventsAnalyzed, analyzedEventIds, createdAt) VALUES (?,?,?,?,?)",
            insertResult);

        given()
            .contentType(ContentType.JSON)
            .body("{" +
                "\"successRate\":83.3," +
                "\"dominantSentiment\":\"POSITIVE\"," +
                "\"totalEventsAnalyzed\":6," +
                "\"analyzedEventIds\":\"1,2,3,4,5,6\"" +
                "}")
            .when()
            .post("/FlexibilityForecasting/forecast")
            .then()
            .statusCode(200)
            .body("forecastId", is(99));
    }

    // ── DELETE /history/{id} ─────────────────────────────────────────────────

    @Test
    void delete_withExistingId_returns204() {
        stubPreparedQuery("DELETE FROM ForecastingResult WHERE id = ?", rowSetWithCount(1));
        given()
            .when()
            .delete("/FlexibilityForecasting/history/1")
            .then()
            .statusCode(204);
    }

    @Test
    void delete_withNonExistingId_returns404() {
        stubPreparedQuery("DELETE FROM ForecastingResult WHERE id = ?", rowSetWithCount(0));
        given()
            .when()
            .delete("/FlexibilityForecasting/history/99")
            .then()
            .statusCode(404);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Row forecastRow(Long id, Float successRate, String sentiment, Integer total,
                            String eventIds, LocalDateTime createdAt) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getFloat("successRate")).thenReturn(successRate);
        Mockito.when(row.getString("dominantSentiment")).thenReturn(sentiment);
        Mockito.when(row.getInteger("totalEventsAnalyzed")).thenReturn(total);
        Mockito.when(row.getString("analyzedEventIds")).thenReturn(eventIds);
        Mockito.when(row.getLocalDateTime("createdAt")).thenReturn(createdAt);
        return row;
    }

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> q = Mockito.mock(Query.class);
        Mockito.when(q.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query(sql)).thenReturn(q);
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

    private RowSet<Row> rowSetOf(Row... rows) {
        RowSet<Row> rs = Mockito.mock(RowSet.class);
        io.vertx.mutiny.sqlclient.RowIterator<Row> it =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Arrays.asList(rows)));
        Mockito.when(rs.iterator()).thenReturn(it);
        return rs;
    }

    private RowSet<Row> rowSetWithCount(int count) {
        RowSet<Row> rs = Mockito.mock(RowSet.class);
        Mockito.when(rs.rowCount()).thenReturn(count);
        return rs;
    }

    private static final class ListRowIterator implements RowIterator<Row> {
        private final java.util.Iterator<Row> it;
        ListRowIterator(List<Row> rows) { this.it = rows.iterator(); }
        @Override public boolean hasNext() { return it.hasNext(); }
        @Override public Row next() { return it.next(); }
    }
}
