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
import org.acme.dto.ForecastResultRequest;
import org.acme.dto.ForecastResultResponse;
import org.acme.dto.OllamaPromptDTO;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private Emitter<String> flexibilityEmitter;

    @BeforeEach
    void setup() {
        resource = new FlexibilityEventResource();
        client = Mockito.mock(MySQLPool.class);
        flexibilityEmitter = Mockito.mock(Emitter.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
        injectEmitter(resource, flexibilityEmitter);
    }

    @Test
    void getFlexibilityEvents_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = flexibilityEventRow(1L, 1L, 1L, "ARBITRAGE_SELL", 95.0f, "DISCHARGE", 150.0f, 10.0f, "GRID_A", timestamp1);
        Row row2 = flexibilityEventRow(2L, 2L, 1L, "BALANCING_UNAVAILABLE", 15.0f, "UNAVAILABLE", null, null, "GRID_B", timestamp2);
        stubQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<FlexibilityEvent> result = resource.get().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).assetId, is(1L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(0).eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(result.get(0).soc_percent, is(95.0f));
        MatcherAssert.assertThat(result.get(0).recommendedAction, is("DISCHARGE"));
        MatcherAssert.assertThat(result.get(0).marketPrice, is(150.0f));
        MatcherAssert.assertThat(result.get(0).incentiveAmount, is(10.0f));
        MatcherAssert.assertThat(result.get(0).gridCellId, is("GRID_A"));
        MatcherAssert.assertThat(result.get(0).timestamp, is(timestamp1));
    }

    @Test
    void getFlexibilityEventById_returnsEntity() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = flexibilityEventRow(1L, 5L, 2L, "ARBITRAGE_SELL", 95.0f, "DISCHARGE", 150.0f, 10.0f, "GRID_A", timestamp);
        stubPreparedQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getSingle(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        FlexibilityEvent entity = (FlexibilityEvent) response.getEntity();
        MatcherAssert.assertThat(entity, notNullValue());
        MatcherAssert.assertThat(entity.id, is(1L));
        MatcherAssert.assertThat(entity.assetId, is(5L));
        MatcherAssert.assertThat(entity.prosumerId, is(2L));
        MatcherAssert.assertThat(entity.eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(entity.soc_percent, is(95.0f));
        MatcherAssert.assertThat(entity.recommendedAction, is("DISCHARGE"));
        MatcherAssert.assertThat(entity.marketPrice, is(150.0f));
        MatcherAssert.assertThat(entity.incentiveAmount, is(10.0f));
        MatcherAssert.assertThat(entity.gridCellId, is("GRID_A"));
        MatcherAssert.assertThat(entity.timestamp, is(timestamp));
    }

    @Test
    void getFlexibilityEventById_returnsNotFound() {
        stubPreparedQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE id = ?", rowSetWithRows());

        Response response = resource.getSingle(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getFlexibilityEventsByAssetId_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = flexibilityEventRow(1L, 5L, 1L, "ARBITRAGE_SELL", 95.0f, "DISCHARGE", 150.0f, 10.0f, "GRID_A", timestamp1);
        Row row2 = flexibilityEventRow(2L, 5L, 2L, "BALANCING_UNAVAILABLE", 15.0f, "UNAVAILABLE", null, null, "GRID_B", timestamp2);
        stubPreparedQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE assetId = ? ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<FlexibilityEvent> result = resource.getByAsset(5L).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).assetId, is(5L));
        MatcherAssert.assertThat(result.get(1).assetId, is(5L));
    }

    @Test
    void getFlexibilityEventsByEventType_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = flexibilityEventRow(1L, 1L, 1L, "ARBITRAGE_SELL", 95.0f, "DISCHARGE", 150.0f, 10.0f, "GRID_A", timestamp1);
        Row row2 = flexibilityEventRow(2L, 2L, 1L, "ARBITRAGE_SELL", 92.0f, "DISCHARGE", 150.0f, 4.0f, "GRID_B", timestamp2);
        stubPreparedQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE eventType = ? ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<FlexibilityEvent> result = resource.getByType("ARBITRAGE_SELL").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(result.get(1).eventType, is("ARBITRAGE_SELL"));
    }

    @Test
    void evaluateTelemetry_highSoC_returnsArbitrageSellEvent() {
        TelemetryDTO telemetry = createTelemetryDTO(1L, 95.0f, "GRID_A");

        Response response = resource.evaluateTelemetry(telemetry, 1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        FlexibilityEvent event = (FlexibilityEvent) response.getEntity();
        MatcherAssert.assertThat(event, notNullValue());
        MatcherAssert.assertThat(event.id, is((Long) null));
        MatcherAssert.assertThat(event.eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(event.recommendedAction, is("DISCHARGE"));
        MatcherAssert.assertThat(event.marketPrice, is(150.0f));
        MatcherAssert.assertThat(event.incentiveAmount, is(10.0f));

        Mockito.verify(flexibilityEmitter, Mockito.never()).send(Mockito.anyString());
    }

    @Test
    void evaluateTelemetry_lowSoC_returnsBalancingUnavailableEvent() {
        TelemetryDTO telemetry = createTelemetryDTO(1L, 15.0f, "GRID_A");

        Response response = resource.evaluateTelemetry(telemetry, 1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        FlexibilityEvent event = (FlexibilityEvent) response.getEntity();
        MatcherAssert.assertThat(event, notNullValue());
        MatcherAssert.assertThat(event.id, is((Long) null));
        MatcherAssert.assertThat(event.eventType, is("BALANCING_UNAVAILABLE"));
        MatcherAssert.assertThat(event.recommendedAction, is("UNAVAILABLE"));
        MatcherAssert.assertThat(event.marketPrice, is((Float) null));
        MatcherAssert.assertThat(event.incentiveAmount, is((Float) null));

        Mockito.verify(flexibilityEmitter, Mockito.never()).send(Mockito.anyString());
    }

    @Test
    void evaluateTelemetry_normalSoC_returnsNoContent() {
        TelemetryDTO telemetry = createTelemetryDTO(1L, 50.0f, "GRID_A");

        Response response = resource.evaluateTelemetry(telemetry, 1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));

        Mockito.verify(flexibilityEmitter, Mockito.never()).send(Mockito.anyString());
    }

    @Test
    void evaluateTelemetry_nullSoC_returnsNoContent() {
        TelemetryDTO telemetry = createTelemetryDTO(1L, null, "GRID_A");

        Response response = resource.evaluateTelemetry(telemetry, 1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));

        Mockito.verify(flexibilityEmitter, Mockito.never()).send(Mockito.anyString());
    }

    @Test
    void evaluateTelemetry_doesNotSaveOrPublishToKafka() {
        TelemetryDTO telemetry = createTelemetryDTO(1L, 95.0f, "GRID_A");

        resource.evaluateTelemetry(telemetry, 1L).await().indefinitely();

        Mockito.verify(flexibilityEmitter, Mockito.never()).send(Mockito.anyString());
    }

    @Test
    void emitFlexibilityOffer_savesAndPublishesToKafka() {
        FlexibilityEvent event = new FlexibilityEvent();
        event.assetId = 1L;
        event.prosumerId = 1L;
        event.eventType = "ARBITRAGE_SELL";
        event.recommendedAction = "DISCHARGE";
        event.timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);

        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
        stubPreparedQuery("INSERT INTO FlexibilityEvent(assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp) VALUES (?,?,?,?,?,?,?,?,?)", insertResult);

        Response response = resource.emitFlexibilityOffer(event).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        FlexibilityEvent saved = (FlexibilityEvent) response.getEntity();
        MatcherAssert.assertThat(saved.id, is(42L));

        Mockito.verify(flexibilityEmitter, Mockito.times(1)).send(Mockito.anyString());
    }

    @Test
    void emitFlexibilityOffer_publishesCorrectKafkaMessage() {
        FlexibilityEvent event = new FlexibilityEvent();
        event.assetId = 1L;
        event.prosumerId = 1L;
        event.eventType = "ARBITRAGE_SELL";
        event.recommendedAction = "DISCHARGE";
        event.timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);

        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
        stubPreparedQuery("INSERT INTO FlexibilityEvent(assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp) VALUES (?,?,?,?,?,?,?,?,?)", insertResult);

        resource.emitFlexibilityOffer(event).await().indefinitely();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(flexibilityEmitter).send(captor.capture());
        String message = captor.getValue();
        MatcherAssert.assertThat(message.contains("\"eventId\":42"), is(true));
        MatcherAssert.assertThat(message.contains("\"assetId\":1"), is(true));
        MatcherAssert.assertThat(message.contains("\"prosumerId\":1"), is(true));
        MatcherAssert.assertThat(message.contains("\"eventType\":\"ARBITRAGE_SELL\""), is(true));
        MatcherAssert.assertThat(message.contains("\"recommendedAction\":\"DISCHARGE\""), is(true));
        MatcherAssert.assertThat(message.contains("\"timestamp\":"), is(true));
    }

    @Test
    void getLogs_withTimeWindow_returnsList() {
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 12, 31, 23, 59);
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 6, 20, 14, 0);
        Row row1 = flexibilityEventRow(1L, 1L, 1L, "ARBITRAGE_SELL", 95.0f, "DISCHARGE", 150.0f, 10.0f, "GRID_A", timestamp1);
        Row row2 = flexibilityEventRow(2L, 2L, 1L, "BALANCING_UNAVAILABLE", 15.0f, "UNAVAILABLE", null, null, "GRID_B", timestamp2);
        stubPreparedQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<FlexibilityEvent> result = resource.getLogs(from.toString(), to.toString()).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).eventType, is("ARBITRAGE_SELL"));
        MatcherAssert.assertThat(result.get(1).id, is(2L));
        MatcherAssert.assertThat(result.get(1).eventType, is("BALANCING_UNAVAILABLE"));
    }

    @Test
    void getLogs_withTimeWindow_returnsEmptyList() {
        LocalDateTime from = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2020, 12, 31, 23, 59);
        stubPreparedQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC", rowSetWithRows());

        List<FlexibilityEvent> result = resource.getLogs(from.toString(), to.toString()).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(0));
    }

    @Test
    void getPrompt_withEvents_returnsPromptString() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = flexibilityEventRow(1L, 1L, 1L, "ARBITRAGE_SELL", 95.0f, "DISCHARGE", 150.0f, 10.0f, "GRID_A", timestamp);
        stubQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent ORDER BY timestamp DESC", rowSetWithRows(row));

        OllamaPromptDTO result = resource.getPrompt().await().indefinitely();
        MatcherAssert.assertThat(result, notNullValue());
        MatcherAssert.assertThat(result.prompt, notNullValue());
        MatcherAssert.assertThat(result.prompt.contains("ARBITRAGE_SELL"), is(true));
        MatcherAssert.assertThat(result.prompt.contains("DISCHARGE"), is(true));
    }

    @Test
    void getPrompt_withNoEvents_returnsEmptyPrompt() {
        stubQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent ORDER BY timestamp DESC", rowSetWithRows());

        OllamaPromptDTO result = resource.getPrompt().await().indefinitely();
        MatcherAssert.assertThat(result, notNullValue());
        MatcherAssert.assertThat(result.prompt.contains("Events:"), is(true));
    }

    @Test
    void persistForecast_returnsForecastId() {
        ForecastResultRequest request = new ForecastResultRequest();
        request.windowStart = "2024-01-01T00:00:00";
        request.windowEnd = "2024-01-31T23:59:59";
        request.flexibilityEventsCount = 10;
        request.gridBalancingCount = 3;
        request.forecastResult = "Positive sentiment, 80% DISCHARGE success rate";

        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(7L);
        stubPreparedQuery("INSERT INTO FlexibilityForecastResult(windowStart, windowEnd, flexibilityEventsCount, gridBalancingCount, forecastResult, createdAt) VALUES (?,?,?,?,?,?)", insertResult);

        Response response = resource.persistForecast(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        ForecastResultResponse body = (ForecastResultResponse) response.getEntity();
        MatcherAssert.assertThat(body.forecastId, is(7L));
    }

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

    private void injectEmitter(FlexibilityEventResource target, Emitter<String> emitter) {
        try {
            Field field = FlexibilityEventResource.class.getDeclaredField("flexibilityEmitter");
            field.setAccessible(true);
            field.set(target, emitter);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject flexibilityEmitter", e);
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

    private RowSet<Row> rowSetWithRowCount(int rowCount) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(rowCount);
        io.vertx.mutiny.sqlclient.RowIterator<Row> iterator =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Collections.emptyList()));
        Mockito.when(rowSet.iterator()).thenReturn(iterator);
        return rowSet;
    }

    private Row flexibilityEventRow(Long id, Long assetId, Long prosumerId, String eventType,
                                   Float soc_percent, String recommendedAction, Float marketPrice,
                                   Float incentiveAmount, String gridCellId, LocalDateTime timestamp) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("assetId")).thenReturn(assetId);
        Mockito.when(row.getLong("prosumerId")).thenReturn(prosumerId);
        Mockito.when(row.getString("eventType")).thenReturn(eventType);
        Mockito.when(row.getFloat("soc_percent")).thenReturn(soc_percent);
        Mockito.when(row.getString("recommendedAction")).thenReturn(recommendedAction);
        Mockito.when(row.getFloat("marketPrice")).thenReturn(marketPrice);
        Mockito.when(row.getFloat("incentiveAmount")).thenReturn(incentiveAmount);
        Mockito.when(row.getString("gridCellId")).thenReturn(gridCellId);
        Mockito.when(row.getLocalDateTime("timestamp")).thenReturn(timestamp);
        return row;
    }

    private TelemetryDTO createTelemetryDTO(Long assetId, Float soc, String gridCellId) {
        TelemetryDTO dto = new TelemetryDTO();
        dto.asset_id = assetId;
        dto.State_of_Charge = soc;
        dto.grid_cell_id = gridCellId;
        dto.asset_type = "BATTERY";
        dto.timeStamp = LocalDateTime.now();
        return dto;
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
