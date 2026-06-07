package org.acme;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import jakarta.ws.rs.core.Response;
import org.acme.dto.BuildPromptRequest;
import org.acme.dto.BuildPromptResponse;
import org.acme.dto.ForecastPersistRequest;
import org.acme.dto.ForecastPersistResponse;
import org.acme.entities.ForecastingResult;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

class FlexibilityForecastingResourceTest {

    FlexibilityForecastingResource resource;
    MySQLPool client;

    @BeforeEach
    void setup() {
        resource = new FlexibilityForecastingResource();
        client = Mockito.mock(MySQLPool.class);
        injectField(resource, "client", client);
        injectField(resource, "schemaCreate", false);
    }

    // ── build prompt ─────────────────────────────────────────────────────────

    @Test
    void buildPrompt_returnsPromptContainingKeyFields() {
        BuildPromptRequest req = new BuildPromptRequest();
        req.eventId = 1L;
        req.assetId = 1001L;
        req.eventType = "ARBITRAGE_SELL";
        req.recommendedAction = "DISCHARGE";
        req.socAtEventTime = 95.2f;
        req.sohAtEventTime = 92.5f;
        req.marketPriceLevel = "HIGH";
        req.gridCellId = "LISBON-DT";
        req.currentSoc = 90.9f;
        req.currentOutputKw = 5.5f;
        req.currentStatus = "ONLINE";

        Response response = resource.buildPrompt(req);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        BuildPromptResponse body = (BuildPromptResponse) response.getEntity();
        MatcherAssert.assertThat(body.prompt.contains("1001"), is(true));
        MatcherAssert.assertThat(body.prompt.contains("LISBON-DT"), is(true));
        MatcherAssert.assertThat(body.prompt.contains("DISCHARGE"), is(true));
        MatcherAssert.assertThat(body.prompt.contains("95.2"), is(true));
        MatcherAssert.assertThat(body.prompt.contains("discharging"), is(true));
    }

    @Test
    void buildPrompt_nullTelemetry_doesNotCrash() {
        BuildPromptRequest req = new BuildPromptRequest();
        req.eventId = 2L;
        req.assetId = 1008L;
        req.eventType = "BALANCING_UNAVAILABLE";
        req.recommendedAction = "UNAVAILABLE";
        req.gridCellId = "FARO-RS";

        Response response = resource.buildPrompt(req);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        BuildPromptResponse body = (BuildPromptResponse) response.getEntity();
        MatcherAssert.assertThat(body.prompt.isBlank(), is(false));
    }

    // ── persist forecast ─────────────────────────────────────────────────────

    @Test
    void persistForecast_persistsAndReturnsId() {
        stubPreparedInsert(
            "INSERT INTO ForecastingResult(successRate, dominantSentiment, totalEventsAnalyzed, analyzedEventIds, createdAt) VALUES (?,?,?,?,?)",
            42L);

        ForecastPersistRequest req = new ForecastPersistRequest();
        req.successRate = 83.3f;
        req.dominantSentiment = "POSITIVE";
        req.totalEventsAnalyzed = 6;
        req.analyzedEventIds = "[1,2,3,4,5,6]";

        Response response = resource.persistForecast(req).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        ForecastPersistResponse body = (ForecastPersistResponse) response.getEntity();
        MatcherAssert.assertThat(body.forecastId, is(42L));
    }

    @Test
    void persistForecast_nullFields_usesDefaults() {
        stubPreparedInsert(
            "INSERT INTO ForecastingResult(successRate, dominantSentiment, totalEventsAnalyzed, analyzedEventIds, createdAt) VALUES (?,?,?,?,?)",
            1L);

        ForecastPersistRequest req = new ForecastPersistRequest();

        Response response = resource.persistForecast(req).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
    }

    // ── history endpoints ────────────────────────────────────────────────────

    @Test
    void getHistory_returnsList() {
        LocalDateTime ts1 = LocalDateTime.of(2026, 6, 7, 10, 0);
        LocalDateTime ts2 = LocalDateTime.of(2026, 6, 7, 11, 0);
        Row row1 = forecastRow(1L, 83.3f, "POSITIVE", 6, "[1,2,3]", ts1);
        Row row2 = forecastRow(2L, 50.0f, "NEUTRAL",  2, "[4,5]",   ts2);
        stubQuery("SELECT * FROM ForecastingResult ORDER BY createdAt DESC", rowSetOf(row1, row2));

        List<ForecastingResult> result = resource.getAllHistory().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).dominantSentiment, is("POSITIVE"));
        MatcherAssert.assertThat(result.get(1).successRate, is(50.0f));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime ts = LocalDateTime.of(2026, 6, 7, 10, 0);
        Row row = forecastRow(1L, 83.3f, "POSITIVE", 6, "[1,2,3]", ts);
        stubPreparedQuery("SELECT * FROM ForecastingResult WHERE id = ?", rowSetOf(row));

        Response response = resource.getHistoryById(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        ForecastingResult result = (ForecastingResult) response.getEntity();
        MatcherAssert.assertThat(result.id, is(1L));
        MatcherAssert.assertThat(result.successRate, is(83.3f));
        MatcherAssert.assertThat(result.dominantSentiment, is("POSITIVE"));
        MatcherAssert.assertThat(result.totalEventsAnalyzed, is(6));
    }

    @Test
    void getById_notFound_returns404() {
        stubPreparedQuery("SELECT * FROM ForecastingResult WHERE id = ?", rowSetOf());
        Response response = resource.getHistoryById(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void delete_existingId_returns204() {
        stubPreparedQuery("DELETE FROM ForecastingResult WHERE id = ?", rowSetWithCount(1));
        Response response = resource.deleteHistory(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void delete_nonExistingId_returns404() {
        stubPreparedQuery("DELETE FROM ForecastingResult WHERE id = ?", rowSetWithCount(0));
        Response response = resource.deleteHistory(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
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

    private void stubPreparedInsert(String sql, Long generatedId) {
        RowSet<Row> rs = Mockito.mock(RowSet.class);
        Mockito.when(rs.property(Mockito.any())).thenReturn(generatedId);
        PreparedQuery<RowSet<Row>> pq = Mockito.mock(PreparedQuery.class);
        Mockito.when(pq.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rs));
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

    private void injectField(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Cannot inject field: " + field, e);
        }
    }

    private static final class ListRowIterator implements RowIterator<Row> {
        private final java.util.Iterator<Row> it;
        ListRowIterator(List<Row> rows) { this.it = rows.iterator(); }
        @Override public boolean hasNext() { return it.hasNext(); }
        @Override public Row next() { return it.next(); }
    }
}
