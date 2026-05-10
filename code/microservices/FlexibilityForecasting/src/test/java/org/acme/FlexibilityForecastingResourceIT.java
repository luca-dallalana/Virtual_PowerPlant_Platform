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
import org.acme.dto.ForecastResult;
import org.acme.services.ForecastingService;
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

@QuarkusTest
class FlexibilityForecastingResourceIT {

    @InjectMock
    MySQLPool client;

    @InjectMock
    ForecastingService forecastingService;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
        Mockito.reset(forecastingService);
    }

    @Test
    void getHistory_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = flexibilityForecastRow(1L, "SENTIMENT", "Test question", "Positive analysis", "POSITIVE", 85.0, 10, 8, 80.0, timestamp1, "{\"key\":\"value\"}");
        Row row2 = flexibilityForecastRow(2L, "SUCCESS_RATE", "Success question", "High success rate", "NEUTRAL", 90.0, 20, 18, 90.0, timestamp2, "{\"metric\":\"success\"}");
        stubQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast ORDER BY analysisTimestamp DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/FlexibilityForecasting/history")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].analysisType", is("SENTIMENT"))
            .body("[0].question", is("Test question"))
            .body("[0].aiResponse", is("Positive analysis"))
            .body("[0].sentiment", is("POSITIVE"))
            .body("[0].confidenceScore", is(85.0f))
            .body("[0].eventsAnalyzed", is(10))
            .body("[0].correlatedOutcomes", is(8))
            .body("[0].successRate", is(80.0f));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = flexibilityForecastRow(1L, "SENTIMENT", "Test question", "Positive analysis", "POSITIVE", 85.0, 10, 8, 80.0, timestamp, "{\"key\":\"value\"}");
        stubPreparedQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast WHERE id = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/FlexibilityForecasting/history/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("analysisType", is("SENTIMENT"))
            .body("sentiment", is("POSITIVE"))
            .body("confidenceScore", is(85.0f));
    }

    @Test
    void getById_returnsNotFound() {
        stubPreparedQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast WHERE id = ?", rowSetWithRows());

        given()
            .when()
            .get("/FlexibilityForecasting/history/99")
            .then()
            .statusCode(404);
    }

    @Test
    void getByAnalysisType_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row1 = flexibilityForecastRow(1L, "SENTIMENT", "Question 1", "Answer 1", "POSITIVE", 85.0, 10, 8, 80.0, timestamp, "{}");
        Row row2 = flexibilityForecastRow(2L, "SENTIMENT", "Question 2", "Answer 2", "POSITIVE", 87.0, 12, 10, 83.0, timestamp, "{}");
        stubPreparedQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast WHERE analysisType = ? ORDER BY analysisTimestamp DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/FlexibilityForecasting/history/type/SENTIMENT")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].analysisType", is("SENTIMENT"))
            .body("[1].analysisType", is("SENTIMENT"));
    }

    @Test
    void analyze_withValidRequest_returnsResult() {
        ForecastResult expectedResult = createForecastResult("GENERAL");

        Mockito.when(forecastingService.performAnalysis(Mockito.any()))
               .thenReturn(Uni.createFrom().item(expectedResult));

        Map<String, Object> body = new HashMap<>();
        body.put("analysisType", "GENERAL");
        body.put("customQuestion", "Test question");
        body.put("events", Arrays.asList());
        body.put("recommendations", Arrays.asList());

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/FlexibilityForecasting/analyze")
            .then()
            .statusCode(200)
            .body("analysisType", is("GENERAL"))
            .body("confidenceScore", is(85.0f))
            .body("id", is(1));
    }

    @Test
    void analyzeSentiment_returnsResult() {
        ForecastResult expectedResult = createForecastResult("SENTIMENT");
        expectedResult.sentiment = "POSITIVE";

        Mockito.when(forecastingService.performAnalysis(Mockito.any()))
               .thenReturn(Uni.createFrom().item(expectedResult));

        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/FlexibilityForecasting/analyze/sentiment")
            .then()
            .statusCode(200)
            .body("analysisType", is("SENTIMENT"))
            .body("sentiment", is("POSITIVE"));
    }

    @Test
    void analyzeSuccessRate_withFilters_returnsResult() {
        ForecastResult expectedResult = createForecastResult("SUCCESS_RATE");
        expectedResult.successRate = 80.0;

        Mockito.when(forecastingService.performAnalysis(Mockito.any()))
               .thenReturn(Uni.createFrom().item(expectedResult));

        given()
            .contentType(ContentType.JSON)
            .queryParam("eventType", "CHARGE")
            .queryParam("recommendedAction", "REDUCE_LOAD")
            .when()
            .post("/FlexibilityForecasting/analyze/success-rate")
            .then()
            .statusCode(200)
            .body("analysisType", is("SUCCESS_RATE"))
            .body("successRate", is(80.0f));
    }

    @Test
    void delete_withExistingId_returns204() {
        RowSet<Row> deleteResult = rowSetWithRowCount(1);
        stubPreparedQuery("DELETE FROM FlexibilityForecast WHERE id = ?", deleteResult);

        given()
            .when()
            .delete("/FlexibilityForecasting/history/1")
            .then()
            .statusCode(204);
    }

    @Test
    void delete_withNonExistingId_returns404() {
        RowSet<Row> deleteResult = rowSetWithRowCount(0);
        stubPreparedQuery("DELETE FROM FlexibilityForecast WHERE id = ?", deleteResult);

        given()
            .when()
            .delete("/FlexibilityForecasting/history/99")
            .then()
            .statusCode(404);
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
