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
import org.acme.dto.*;
import org.acme.entities.ForecastingResult;
import org.acme.services.DataCorrelationService;
import org.acme.services.PromptBuilder;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

class FlexibilityForecastingResourceTest {

    FlexibilityForecastingResource resource;
    private MySQLPool client;
    private DataCorrelationService correlationService;
    private PromptBuilder promptBuilder;

    @BeforeEach
    void setup() {
        resource = new FlexibilityForecastingResource();
        client = Mockito.mock(MySQLPool.class);
        correlationService = new DataCorrelationService();
        promptBuilder = new PromptBuilder();
        injectField(resource, "client", client);
        injectField(resource, "schemaCreate", false);
        injectField(resource, "correlationService", correlationService);
        injectField(resource, "promptBuilder", promptBuilder);
    }

    // ── evaluate-correlation ─────────────────────────────────────────────────

    @Test
    void evaluateCorrelation_emptyInputs_returnsZeroStats() {
        EventCorrelationRequest req = new EventCorrelationRequest();
        req.flexibilityLogs = Collections.emptyList();
        req.gridBalancingLogs = Collections.emptyList();
        req.solarAssets = Collections.emptyList();
        req.solarTelemetry = Collections.emptyList();

        Response response = resource.evaluateCorrelation(req);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        EventCorrelationResult result = (EventCorrelationResult) response.getEntity();
        MatcherAssert.assertThat(result.totalFlexibilityEvents, is(0));
        MatcherAssert.assertThat(result.solarAssetCount, is(0));
        MatcherAssert.assertThat(result.avgCurrentGenerationKw, is(0.0));
    }

    @Test
    void evaluateCorrelation_withSolarTelemetry_aggregatesByGridCell() {
        EventCorrelationRequest req = new EventCorrelationRequest();
        req.flexibilityLogs = Collections.emptyList();
        req.gridBalancingLogs = Collections.emptyList();
        req.solarAssets = Collections.singletonList(createSolarAsset(1002L));

        SolarTelemetryDTO t1 = new SolarTelemetryDTO();
        t1.asset_id = 1002L;
        t1.grid_cell_id = "GRID_A";
        t1.Current_Generation = 5.0f;
        t1.Daily_Total = 20.0f;

        SolarTelemetryDTO t2 = new SolarTelemetryDTO();
        t2.asset_id = 1002L;
        t2.grid_cell_id = "GRID_A";
        t2.Current_Generation = 3.0f;
        t2.Daily_Total = 10.0f;

        req.solarTelemetry = Arrays.asList(t1, t2);

        Response response = resource.evaluateCorrelation(req);
        EventCorrelationResult result = (EventCorrelationResult) response.getEntity();

        MatcherAssert.assertThat(result.solarAssetCount, is(1));
        MatcherAssert.assertThat(result.totalDailyGenerationKwh, is(30.0));
        MatcherAssert.assertThat(result.solarGenerationByGridCell.get("GRID_A"), is(8.0));
    }

    @Test
    void evaluateCorrelation_correlatesEventToRecommendation() {
        FlexibilityEventDTO event = createFlexibilityEventDTO("GRID_A");
        BalancingRecommendationDTO rec = createRecommendation("GRID_A", event.timestamp.plusMinutes(5));

        EventCorrelationRequest req = new EventCorrelationRequest();
        req.flexibilityLogs = Collections.singletonList(event);
        req.gridBalancingLogs = Collections.singletonList(rec);
        req.solarAssets = Collections.emptyList();
        req.solarTelemetry = Collections.emptyList();

        Response response = resource.evaluateCorrelation(req);
        EventCorrelationResult result = (EventCorrelationResult) response.getEntity();

        MatcherAssert.assertThat(result.totalFlexibilityEvents, is(1));
        MatcherAssert.assertThat(result.correlatedOutcomes, is(1));
        MatcherAssert.assertThat(result.correlatedEvents, hasSize(1));
        MatcherAssert.assertThat(result.correlatedEvents.get(0).recommendations, hasSize(1));
    }

    @Test
    void evaluateCorrelation_solarContextAttachedToEvent() {
        FlexibilityEventDTO event = createFlexibilityEventDTO("GRID_A");

        SolarTelemetryDTO solar = new SolarTelemetryDTO();
        solar.asset_id = 1002L;
        solar.grid_cell_id = "GRID_A";
        solar.Current_Generation = 7.5f;
        solar.Daily_Total = 40.0f;

        EventCorrelationRequest req = new EventCorrelationRequest();
        req.flexibilityLogs = Collections.singletonList(event);
        req.gridBalancingLogs = Collections.emptyList();
        req.solarAssets = Collections.emptyList();
        req.solarTelemetry = Collections.singletonList(solar);

        Response response = resource.evaluateCorrelation(req);
        EventCorrelationResult result = (EventCorrelationResult) response.getEntity();

        MatcherAssert.assertThat(result.correlatedEvents.get(0).solarGenerationKw, is(7.5));
    }

    // ── build-prompt ─────────────────────────────────────────────────────────

    @Test
    void buildPrompt_returnsNonBlankPrompt() {
        EventCorrelationResult correlation = new EventCorrelationResult();
        correlation.flexibilityLogs = Collections.singletonList(createFlexibilityEventDTO("GRID_A"));
        correlation.gridBalancingLogs = Collections.emptyList();
        correlation.solarAssets = Collections.emptyList();
        correlation.solarTelemetry = Collections.emptyList();
        correlation.correlatedEvents = Collections.emptyList();
        correlation.solarGenerationByGridCell = Collections.emptyMap();
        correlation.totalFlexibilityEvents = 1;

        Response response = resource.buildPrompt(correlation);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        OllamaPromptResult result = (OllamaPromptResult) response.getEntity();
        MatcherAssert.assertThat(result.prompt, notNullValue());
        MatcherAssert.assertThat(result.prompt.isBlank(), is(false));
        MatcherAssert.assertThat(result.prompt, containsString("FLEXIBILITY_FORECASTING"));
    }

    @Test
    void buildPrompt_includesSolarSection() {
        SolarTelemetryDTO solar = new SolarTelemetryDTO();
        solar.asset_id = 1002L;
        solar.grid_cell_id = "GRID_A";
        solar.Current_Generation = 5.0f;
        solar.Daily_Total = 20.0f;

        EventCorrelationResult correlation = new EventCorrelationResult();
        correlation.flexibilityLogs = Collections.emptyList();
        correlation.gridBalancingLogs = Collections.emptyList();
        correlation.solarAssets = Collections.emptyList();
        correlation.solarTelemetry = Collections.singletonList(solar);
        correlation.correlatedEvents = Collections.emptyList();
        correlation.solarGenerationByGridCell = Collections.emptyMap();

        Response response = resource.buildPrompt(correlation);
        OllamaPromptResult result = (OllamaPromptResult) response.getEntity();
        MatcherAssert.assertThat(result.prompt, containsString("Solar telemetry sample"));
    }

    // ── persist forecast ─────────────────────────────────────────────────────

    @Test
    void persistForecast_persistsAndReturnsId() {
        stubPreparedInsert("INSERT INTO ForecastingResult(forecastResult, windowStart, windowEnd, flexibilityEventsCount, gridBalancingCount, createdAt) VALUES (?,?,?,?,?,?)", 42L);

        ForecastPersistRequest req = new ForecastPersistRequest();
        req.forecastResult = "{\"summary\":\"all good\"}";

        Response response = resource.persistForecast(req).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        ForecastPersistResponse body = (ForecastPersistResponse) response.getEntity();
        MatcherAssert.assertThat(body.forecastId, is(42L));
    }

    // ── history endpoints ────────────────────────────────────────────────────

    @Test
    void getHistory_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = forecastingResultRow(1L, "Forecast A", "2024-01-15T10:00", "2024-01-15T10:30", 5, 3, timestamp1);
        Row row2 = forecastingResultRow(2L, "Forecast B", "2024-01-15T11:00", "2024-01-15T11:30", 8, 4, timestamp2);
        stubQuery("SELECT * FROM ForecastingResult ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        List<ForecastingResult> result = resource.getAllHistory().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).forecastResult, is("Forecast A"));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = forecastingResultRow(1L, "Forecast A", "2024-01-15T10:00", "2024-01-15T10:30", 5, 3, timestamp);
        stubPreparedQuery("SELECT * FROM ForecastingResult WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getHistoryById(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        ForecastingResult result = (ForecastingResult) response.getEntity();
        MatcherAssert.assertThat(result.id, is(1L));
        MatcherAssert.assertThat(result.forecastResult, is("Forecast A"));
    }

    @Test
    void getById_returnsNotFound() {
        stubPreparedQuery("SELECT * FROM ForecastingResult WHERE id = ?", rowSetWithRows());
        Response response = resource.getHistoryById(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void delete_withExistingId_returns204() {
        stubPreparedQuery("DELETE FROM ForecastingResult WHERE id = ?", rowSetWithRowCount(1));
        Response response = resource.deleteHistory(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void delete_withNonExistingId_returns404() {
        stubPreparedQuery("DELETE FROM ForecastingResult WHERE id = ?", rowSetWithRowCount(0));
        Response response = resource.deleteHistory(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FlexibilityEventDTO createFlexibilityEventDTO(String gridCellId) {
        FlexibilityEventDTO dto = new FlexibilityEventDTO();
        dto.id = 1L;
        dto.assetId = 100L;
        dto.prosumerId = 10L;
        dto.eventType = "ARBITRAGE_SELL";
        dto.soc_percent = 92.0f;
        dto.recommendedAction = "DISCHARGE";
        dto.marketPrice = 150.0f;
        dto.incentiveAmount = 4.0f;
        dto.gridCellId = gridCellId;
        dto.timestamp = LocalDateTime.now();
        return dto;
    }

    private BalancingRecommendationDTO createRecommendation(String gridCellId, LocalDateTime createdAt) {
        BalancingRecommendationDTO dto = new BalancingRecommendationDTO();
        dto.id = 1L;
        dto.sourceGridCellId = gridCellId;
        dto.targetGridCellId = "GRID_B";
        dto.sourceNetLoadKw = 95.0;
        dto.targetHeadroomKw = 40.0;
        dto.overloadKw = -5.0;
        dto.status = "BALANCED";
        dto.rationale = "Grid balanced";
        dto.createdAt = createdAt;
        return dto;
    }

    private SolarAssetDTO createSolarAsset(Long assetId) {
        SolarAssetDTO dto = new SolarAssetDTO();
        dto.assetId = assetId;
        dto.prosumerId = 1L;
        dto.assetType = "SOLAR";
        dto.model = "SolarEdge SE7600H";
        dto.status = "ACTIVE";
        return dto;
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject field " + fieldName, e);
        }
    }

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

    private void stubPreparedInsert(String sql, Long generatedId) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.property(Mockito.any())).thenReturn(generatedId);
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
