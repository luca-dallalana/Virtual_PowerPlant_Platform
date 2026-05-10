# FlexibilityForecasting Tests

This folder contains the tests for the `FlexibilityForecasting` microservice. The code under test is the `FlexibilityForecasting` model and the `FlexibilityForecastingResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/FlexibilityForecastingResourceTest.java`: unit tests for the `FlexibilityForecastingResource` and the `FlexibilityForecast` domain behaviour.
- `src/test/java/org/acme/FlexibilityForecastingResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `FlexibilityForecastingResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The integration tests also mock the `ForecastingService` to verify the API endpoints independently of the forecasting logic.

## What Is Covered

### Unit tests: `FlexibilityForecastingResourceTest`

These tests validate the behavior of the `FlexibilityForecast` model and `FlexibilityForecastingResource` in isolation:

- `getHistory_returnsList`: verifies that `getAllHistory()` returns a list of `FlexibilityForecast` objects mapped from DB `Row` instances, including `id`, `analysisType`, `question`, `aiResponse`, `sentiment`, `confidenceScore`, `eventsAnalyzed`, `correlatedOutcomes`, `successRate`, `analysisTimestamp`, and `insightsJson`.
- `getById_returnsEntity`: verifies that `getHistoryById(id)` returns the matching entity when a row exists and that the `Response` status is `200`.
- `getById_returnsNotFound`: verifies that `getHistoryById(id)` returns `404` when the prepared query returns no rows.
- `getByAnalysisType_returnsFiltered`: verifies that `getHistoryByType(analysisType)` returns only forecasts with the requested `analysisType` and that results are ordered by `analysisTimestamp` descending.
- `analyze_withValidRequest_returnsResult`: verifies that posting an analysis request with `ForecastRequest` invokes `ForecastingService.performAnalysis()` and returns a `ForecastResult` with the expected `analysisType`, `confidenceScore`, and `id`.
- `analyzeSentiment_createsAnalysis`: verifies that posting to the sentiment analysis endpoint invokes `ForecastingService.performAnalysis()` with `analysisType` = `SENTIMENT` and returns a `ForecastResult` with `sentiment` = `POSITIVE`.
- `analyzeSuccessRate_withFilters_returnsResult`: verifies that posting to the success rate endpoint with query parameters `eventType` and `recommendedAction` returns a `ForecastResult` with `analysisType` = `SUCCESS_RATE` and the expected `successRate` value.
- `analyzeSuccessRate_withoutFilters_returnsResult`: verifies that posting to the success rate endpoint without filters returns a `ForecastResult` with `analysisType` = `SUCCESS_RATE` and a `successRate` value.
- `delete_withExistingId_returns204`: verifies that `deleteHistory(id)` deletes the record and returns `204 No Content` when the record exists.
- `delete_withNonExistingId_returns404`: verifies that `deleteHistory(id)` returns `404` when the record does not exist.

The unit tests also validate the exact SQL used by the model/resource methods:

- `SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast ORDER BY analysisTimestamp DESC`
- `SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast WHERE id = ?`
- `SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson FROM FlexibilityForecast WHERE analysisType = ? ORDER BY analysisTimestamp DESC`
- `DELETE FROM FlexibilityForecast WHERE id = ?`

### Integration tests: `FlexibilityForecastingResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured:

- `getHistory_returnsList`: verifies `GET /FlexibilityForecasting/history` returns a JSON array with all records including all fields.
- `getById_returnsEntity`: verifies `GET /FlexibilityForecasting/history/{id}` returns one record when it exists with status `200`.
- `getById_returnsNotFound`: verifies `GET /FlexibilityForecasting/history/{id}` returns status `404` when the record does not exist.
- `getByAnalysisType_returnsFiltered`: verifies `GET /FlexibilityForecasting/history/type/{analysisType}` returns a JSON array with only records matching the requested `analysisType`.
- `analyze_withValidRequest_returnsResult`: verifies `POST /FlexibilityForecasting/analyze` with a `ForecastRequest` body returns a `ForecastResult` JSON object with the expected `analysisType`, `confidenceScore`, and `id`.
- `analyzeSentiment_returnsResult`: verifies `POST /FlexibilityForecasting/analyze/sentiment` returns a `ForecastResult` with `analysisType` = `SENTIMENT` and `sentiment` = `POSITIVE`.
- `analyzeSuccessRate_withFilters_returnsResult`: verifies `POST /FlexibilityForecasting/analyze/success-rate` with query parameters `eventType` and `recommendedAction` returns a `ForecastResult` with `analysisType` = `SUCCESS_RATE` and the expected `successRate` value.
- `delete_withExistingId_returns204`: verifies `DELETE /FlexibilityForecasting/history/{id}` returns status `204 No Content` when the record exists.
- `delete_withNonExistingId_returns404`: verifies `DELETE /FlexibilityForecasting/history/{id}` returns status `404` when the record does not exist.

## How To Run

From the `FlexibilityEvent` module root:

To run only the unit test class:

```bash
mvn test
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.push=false
```