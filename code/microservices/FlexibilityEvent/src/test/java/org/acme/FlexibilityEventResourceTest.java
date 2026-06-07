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
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;

class FlexibilityEventResourceTest {

    FlexibilityEventResource resource;
    private MySQLPool client;

    @BeforeEach
    void setup() {
        resource = new FlexibilityEventResource();
        client = Mockito.mock(MySQLPool.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
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

        List<FlexibilityEvent> result = resource.get().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).assetId, is(1L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(0).eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(result.get(0).soc_percent, is(95.0f));
        MatcherAssert.assertThat(result.get(0).soh_percent, is(92.5f));
        MatcherAssert.assertThat(result.get(0).recommendedAction, is("DISCHARGE"));
        MatcherAssert.assertThat(result.get(0).marketPriceLevel, is("HIGH"));
        MatcherAssert.assertThat(result.get(0).gridCellId, is("GRID_A"));
        MatcherAssert.assertThat(result.get(0).timestamp, is(timestamp1));
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

        Response response = resource.getSingle(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        FlexibilityEvent entity = (FlexibilityEvent) response.getEntity();
        MatcherAssert.assertThat(entity, notNullValue());
        MatcherAssert.assertThat(entity.id, is(1L));
        MatcherAssert.assertThat(entity.assetId, is(5L));
        MatcherAssert.assertThat(entity.prosumerId, is(2L));
        MatcherAssert.assertThat(entity.eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(entity.soc_percent, is(95.0f));
        MatcherAssert.assertThat(entity.soh_percent, is(92.5f));
        MatcherAssert.assertThat(entity.recommendedAction, is("DISCHARGE"));
        MatcherAssert.assertThat(entity.marketPriceLevel, is("HIGH"));
        MatcherAssert.assertThat(entity.gridCellId, is("GRID_A"));
        MatcherAssert.assertThat(entity.timestamp, is(timestamp));
    }

    @Test
    void getFlexibilityEventById_returnsNotFound() {
        stubPreparedQuery(
            "SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp " +
            "FROM FlexibilityEvent WHERE id = ?",
            rowSetWithRows());

        Response response = resource.getSingle(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
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

        List<FlexibilityEvent> result = resource.getByAsset(5L).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).assetId, is(5L));
        MatcherAssert.assertThat(result.get(1).assetId, is(5L));
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

        List<FlexibilityEvent> result = resource.getByType("ARBITRAGE_SELL").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(result.get(1).eventType, is("ARBITRAGE_SELL"));
    }

    @Test
    void getLogsByMinutes_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 6, 20, 14, 0);
        Row row1 = flexibilityEventRow(1L, 1L, 1L, "ARBITRAGE_SELL", 95.0f, 92.5f, "DISCHARGE", "HIGH", "GRID_A", timestamp1);
        Row row2 = flexibilityEventRow(2L, 2L, 1L, "BALANCING_UNAVAILABLE", 15.0f, 75.0f, "UNAVAILABLE", null, "GRID_B", timestamp2);
        stubPreparedQuery(
            "SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp " +
            "FROM FlexibilityEvent WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC",
            rowSetWithRows(row1, row2));

        List<FlexibilityEvent> result = resource.getLogsByMinutes(20).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(result.get(1).id, is(2L));
        MatcherAssert.assertThat(result.get(1).eventType, is("BALANCING_UNAVAILABLE"));
    }

    @Test
    void getLogsByMinutes_returnsEmptyList() {
        stubPreparedQuery(
            "SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp " +
            "FROM FlexibilityEvent WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC",
            rowSetWithRows());

        List<FlexibilityEvent> result = resource.getLogsByMinutes(20).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(0));
    }

    @Test
    void saveBatch_persistsEventsAndReturnsWithIds() {
        FlexibilityEvent event = new FlexibilityEvent();
        event.assetId = 1001L;
        event.prosumerId = 1L;
        event.eventType = "ARBITRAGE_SELL";
        event.recommendedAction = "DISCHARGE";
        event.soc_percent = 95.0f;
        event.soh_percent = 92.5f;
        event.marketPriceLevel = "HIGH";
        event.gridCellId = "LISBON-DT";
        event.timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);

        RowSet<Row> insertResult = rowSetWithInsertedId(42L);
        stubPreparedQuery(
            "INSERT INTO FlexibilityEvent(assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp) VALUES (?,?,?,?,?,?,?,?,?)",
            insertResult);

        Response response = resource.saveBatch(List.of(event)).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        @SuppressWarnings("unchecked")
        List<FlexibilityEvent> saved = (List<FlexibilityEvent>) response.getEntity();
        MatcherAssert.assertThat(saved, hasSize(1));
        MatcherAssert.assertThat(saved.get(0).id, is(42L));
        MatcherAssert.assertThat(saved.get(0).eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(saved.get(0).marketPriceLevel, is("HIGH"));
    }

    @Test
    void saveBatch_emptyList_returnsEmptyResponse() {
        Response response = resource.saveBatch(Collections.emptyList()).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        @SuppressWarnings("unchecked")
        List<FlexibilityEvent> saved = (List<FlexibilityEvent>) response.getEntity();
        MatcherAssert.assertThat(saved, hasSize(0));
    }

    @Test
    void saveBatch_nullTimestamp_getsAutoPopulated() {
        FlexibilityEvent event = new FlexibilityEvent();
        event.assetId = 1001L;
        event.prosumerId = 1L;
        event.eventType = "ARBITRAGE_SELL";
        event.recommendedAction = "DISCHARGE";
        event.soc_percent = 95.0f;
        event.soh_percent = 92.5f;
        event.marketPriceLevel = "HIGH";
        event.gridCellId = "LISBON-DT";
        // timestamp intentionally left null

        RowSet<Row> insertResult = rowSetWithInsertedId(10L);
        stubPreparedQuery(
            "INSERT INTO FlexibilityEvent(assetId, prosumerId, eventType, soc_percent, soh_percent, " +
            "recommendedAction, marketPriceLevel, gridCellId, timestamp) VALUES (?,?,?,?,?,?,?,?,?)",
            insertResult);

        resource.saveBatch(List.of(event)).await().indefinitely();
        MatcherAssert.assertThat(event.timestamp, notNullValue());
    }

    // --- helpers ---

    private void injectClient(FlexibilityEventResource target, MySQLPool pool) {
        try {
            Field field = FlexibilityEventResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(FlexibilityEventResource target, boolean value) {
        try {
            Field field = FlexibilityEventResource.class.getDeclaredField("schemaCreate");
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject schemaCreate", e);
        }
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

    private RowSet<Row> rowSetWithInsertedId(Long insertedId) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(insertedId);
        io.vertx.mutiny.sqlclient.RowIterator<Row> iterator =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Collections.emptyList()));
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
