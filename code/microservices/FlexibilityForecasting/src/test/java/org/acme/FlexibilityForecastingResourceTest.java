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
import org.acme.dto.BalancingRecommendationDTO;
import org.acme.dto.FlexibilityEventDTO;
import org.acme.dto.ForecastRequest;
import org.acme.dto.ForecastResult;
import org.acme.entities.FlexibilityForecast;
import org.acme.services.ForecastingService;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

class FlexibilityForecastingResourceTest {

    FlexibilityForecastingResource resource;
    private MySQLPool client;
    private ForecastingService forecastingService;

    @BeforeEach
    void setup() {
        resource = new FlexibilityForecastingResource();
        client = Mockito.mock(MySQLPool.class);
        forecastingService = Mockito.mock(ForecastingService.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
        injectService(resource, forecastingService);
    }

    @Test
    void getHistory_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = flexibilityForecastRow(1L, "SENTIMENT", "Test question", "Positive analysis", "POSITIVE", 85.0, 10, 8, 80.0, timestamp1, "{\"key\":\"value\"}");
        Row row2 = flexibilityForecastRow(2L, "SUCCESS_RATE", "Success question", "High success rate", "NEUTRAL", 90.0, 20, 18, 90.0, timestamp2, "{\"metric\":\"success\"}");
        stubQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast ORDER BY analysisTimestamp DESC", rowSetWithRows(row1, row2));

        List<FlexibilityForecast> result = resource.getAllHistory().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).analysisType, is("SENTIMENT"));
        MatcherAssert.assertThat(result.get(0).question, is("Test question"));
        MatcherAssert.assertThat(result.get(0).aiResponse, is("Positive analysis"));
        MatcherAssert.assertThat(result.get(0).sentiment, is("POSITIVE"));
        MatcherAssert.assertThat(result.get(0).confidenceScore, is(85.0));
        MatcherAssert.assertThat(result.get(0).eventsAnalyzed, is(10));
        MatcherAssert.assertThat(result.get(0).correlatedOutcomes, is(8));
        MatcherAssert.assertThat(result.get(0).successRate, is(80.0));
        MatcherAssert.assertThat(result.get(0).analysisTimestamp, is(timestamp1));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = flexibilityForecastRow(1L, "SENTIMENT", "Test question", "Positive analysis", "POSITIVE", 85.0, 10, 8, 80.0, timestamp, "{\"key\":\"value\"}");
        stubPreparedQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getHistoryById(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        FlexibilityForecast result = (FlexibilityForecast) response.getEntity();
        MatcherAssert.assertThat(result.id, is(1L));
        MatcherAssert.assertThat(result.analysisType, is("SENTIMENT"));
        MatcherAssert.assertThat(result.sentiment, is("POSITIVE"));
        MatcherAssert.assertThat(result.confidenceScore, is(85.0));
    }

    @Test
    void getById_returnsNotFound() {
        stubPreparedQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast WHERE id = ?", rowSetWithRows());

        Response response = resource.getHistoryById(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getByAnalysisType_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row1 = flexibilityForecastRow(1L, "SENTIMENT", "Question 1", "Answer 1", "POSITIVE", 85.0, 10, 8, 80.0, timestamp, "{}");
        Row row2 = flexibilityForecastRow(2L, "SENTIMENT", "Question 2", "Answer 2", "POSITIVE", 87.0, 12, 10, 83.0, timestamp, "{}");
        stubPreparedQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast WHERE analysisType = ? ORDER BY analysisTimestamp DESC", rowSetWithRows(row1, row2));

        List<FlexibilityForecast> result = resource.getHistoryByType("SENTIMENT").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).analysisType, is("SENTIMENT"));
        MatcherAssert.assertThat(result.get(1).analysisType, is("SENTIMENT"));
    }

    @Test
    void analyze_withValidRequest_returnsResult() {
        ForecastRequest request = createForecastRequest("GENERAL");
        ForecastResult expectedResult = createForecastResult("GENERAL");

        Mockito.when(forecastingService.performAnalysis(request))
               .thenReturn(Uni.createFrom().item(expectedResult));

        ForecastResult result = resource.analyze(request).await().indefinitely();
        MatcherAssert.assertThat(result.analysisType, is("GENERAL"));
        MatcherAssert.assertThat(result.confidenceScore, is(85.0));
        MatcherAssert.assertThat(result.id, is(1L));
    }

    @Test
    void analyzeSentiment_createsAnalysis() {
        ForecastResult expectedResult = createForecastResult("SENTIMENT");
        expectedResult.sentiment = "POSITIVE";

        Mockito.when(forecastingService.performAnalysis(Mockito.any(ForecastRequest.class)))
               .thenReturn(Uni.createFrom().item(expectedResult));

        ForecastResult result = resource.analyzeSentiment().await().indefinitely();
        MatcherAssert.assertThat(result.analysisType, is("SENTIMENT"));
        MatcherAssert.assertThat(result.sentiment, is("POSITIVE"));
    }

    @Test
    void analyzeSuccessRate_withFilters_returnsResult() {
        ForecastResult expectedResult = createForecastResult("SUCCESS_RATE");
        expectedResult.successRate = 80.0;

        Mockito.when(forecastingService.performAnalysis(Mockito.any(ForecastRequest.class)))
               .thenReturn(Uni.createFrom().item(expectedResult));

        ForecastResult result = resource.analyzeSuccessRate("CHARGE", "REDUCE_LOAD").await().indefinitely();
        MatcherAssert.assertThat(result.analysisType, is("SUCCESS_RATE"));
        MatcherAssert.assertThat(result.successRate, is(80.0));
    }

    @Test
    void analyzeSuccessRate_withoutFilters_returnsResult() {
        ForecastResult expectedResult = createForecastResult("SUCCESS_RATE");
        expectedResult.successRate = 75.0;

        Mockito.when(forecastingService.performAnalysis(Mockito.any(ForecastRequest.class)))
               .thenReturn(Uni.createFrom().item(expectedResult));

        ForecastResult result = resource.analyzeSuccessRate(null, null).await().indefinitely();
        MatcherAssert.assertThat(result.analysisType, is("SUCCESS_RATE"));
        MatcherAssert.assertThat(result.successRate, is(75.0));
    }

    @Test
    void delete_withExistingId_returns204() {
        RowSet<Row> deleteResult = rowSetWithRowCount(1);
        stubPreparedQuery("DELETE FROM FlexibilityForecast WHERE id = ?", deleteResult);

        Response response = resource.deleteHistory(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void delete_withNonExistingId_returns404() {
        RowSet<Row> deleteResult = rowSetWithRowCount(0);
        stubPreparedQuery("DELETE FROM FlexibilityForecast WHERE id = ?", deleteResult);

        Response response = resource.deleteHistory(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    private void injectClient(FlexibilityForecastingResource target, MySQLPool pool) {
        try {
            Field field = FlexibilityForecastingResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(FlexibilityForecastingResource target, boolean value) {
        try {
            Field field = FlexibilityForecastingResource.class.getDeclaredField("schemaCreate");
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject schemaCreate", e);
        }
    }

    private void injectService(FlexibilityForecastingResource target, ForecastingService service) {
        try {
            Field field = FlexibilityForecastingResource.class.getDeclaredField("forecastingService");
            field.setAccessible(true);
            field.set(target, service);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject ForecastingService", e);
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

    private RowSet<Row> rowSetWithRowCount(int count) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(count);
        return rowSet;
    }

    private Row flexibilityForecastRow(Long id, String analysisType, String question,
                                       String aiResponse, String sentiment, Double confidenceScore,
                                       Integer eventsAnalyzed, Integer correlatedOutcomes,
                                       Double successRate, LocalDateTime analysisTimestamp,
                                       String insightsJson) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("analysisType")).thenReturn(analysisType);
        Mockito.when(row.getString("question")).thenReturn(question);
        Mockito.when(row.getString("aiResponse")).thenReturn(aiResponse);
        Mockito.when(row.getString("sentiment")).thenReturn(sentiment);
        Mockito.when(row.getDouble("confidenceScore")).thenReturn(confidenceScore);
        Mockito.when(row.getInteger("eventsAnalyzed")).thenReturn(eventsAnalyzed);
        Mockito.when(row.getInteger("correlatedOutcomes")).thenReturn(correlatedOutcomes);
        Mockito.when(row.getDouble("successRate")).thenReturn(successRate);
        Mockito.when(row.getLocalDateTime("analysisTimestamp")).thenReturn(analysisTimestamp);
        Mockito.when(row.getString("insightsJson")).thenReturn(insightsJson);
        return row;
    }

    private ForecastRequest createForecastRequest(String analysisType) {
        ForecastRequest request = new ForecastRequest();
        request.analysisType = analysisType;
        request.customQuestion = "Test question";
        request.events = Arrays.asList(createFlexibilityEventDTO());
        request.recommendations = Arrays.asList(createBalancingRecommendationDTO());
        return request;
    }

    private FlexibilityEventDTO createFlexibilityEventDTO() {
        FlexibilityEventDTO dto = new FlexibilityEventDTO();
        dto.id = 1L;
        dto.assetId = 100L;
        dto.prosumerId = 10L;
        dto.eventType = "CHARGE";
        dto.soc_percent = 75.0f;
        dto.recommendedAction = "REDUCE_LOAD";
        dto.marketPrice = 50.0f;
        dto.incentiveAmount = 5.0f;
        dto.gridCellId = "GRID_A";
        dto.timestamp = LocalDateTime.now();
        return dto;
    }

    private BalancingRecommendationDTO createBalancingRecommendationDTO() {
        BalancingRecommendationDTO dto = new BalancingRecommendationDTO();
        dto.id = 1L;
        dto.sourceGridCellId = "GRID_A";
        dto.targetGridCellId = "GRID_B";
        dto.sourceNetLoadKw = 95.0;
        dto.targetHeadroomKw = 40.0;
        dto.overloadKw = -5.0;
        dto.status = "BALANCED";
        dto.rationale = "Grid balanced";
        dto.createdAt = LocalDateTime.now();
        return dto;
    }

    private ForecastResult createForecastResult(String analysisType) {
        ForecastResult result = new ForecastResult();
        result.id = 1L;
        result.analysisType = analysisType;
        result.question = "Test question";
        result.aiResponse = "AI response";
        result.sentiment = "POSITIVE";
        result.confidenceScore = 85.0;
        result.eventsAnalyzed = 10;
        result.correlatedOutcomes = 8;
        result.successRate = 80.0;
        result.analysisTimestamp = LocalDateTime.now();
        result.insights = new HashMap<>();
        result.insights.put("key", "value");
        return result;
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
