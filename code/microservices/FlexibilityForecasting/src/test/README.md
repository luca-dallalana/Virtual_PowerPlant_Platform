# FlexibilityForecasting Tests

This folder contains the tests for the `FlexibilityForecasting` microservice. The code under test is the `ForecastingResult` entity and the `FlexibilityForecastingResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/FlexibilityForecastingResourceTest.java`: unit tests for `FlexibilityForecastingResource` and the `ForecastingResult` domain behaviour.
- `src/test/java/org/acme/FlexibilityForecastingResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `FlexibilityForecastingResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The unit test injects the mock via reflection; the IT test uses `@InjectMock`.

## What Is Covered

### Unit tests: `FlexibilityForecastingResourceTest`

#### Prompt-building tests

| Test | What it verifies |
|---|---|
| `buildPrompt_returnsPromptContainingKeyFields` | `buildPrompt(request)` returns `200` with a prompt containing `assetId`, `gridCellId`, `recommendedAction`, `socAtEventTime`, and the word `"discharging"` when `currentOutputKw > 0` |
| `buildPrompt_nullTelemetry_doesNotCrash` | `buildPrompt(request)` with null telemetry fields returns `200` and a non-blank prompt without throwing |

#### Persist tests

| Test | What it verifies |
|---|---|
| `persistForecast_persistsAndReturnsId` | `persistForecast(request)` inserts the record and returns `200` with `forecastId` set from `LAST_INSERTED_ID` |
| `persistForecast_nullFields_usesDefaults` | `persistForecast(request)` with all-null fields still returns `200` without throwing |

#### History read tests

| Test | What it verifies |
|---|---|
| `getHistory_returnsList` | `getAllHistory()` returns a list of `ForecastingResult` objects including `id`, `successRate`, `dominantSentiment`, `totalEventsAnalyzed`, `analyzedEventIds`, and `createdAt` |
| `getById_returnsEntity` | `getHistoryById(id)` returns the matching entity with status `200` |
| `getById_notFound_returns404` | `getHistoryById(id)` returns `404` when no row exists |

#### Delete tests

| Test | What it verifies |
|---|---|
| `delete_existingId_returns204` | `deleteHistory(id)` returns `204 No Content` when the record exists |
| `delete_nonExistingId_returns404` | `deleteHistory(id)` returns `404` when the record does not exist |

### Integration tests: `FlexibilityForecastingResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured. `MySQLPool` is replaced with a Mockito mock via `@InjectMock`.

| Test | Endpoint | What it verifies |
|---|---|---|
| `getHistory_returnsList` | `GET /FlexibilityForecasting/history` | Returns a JSON array with `id`, `successRate`, `dominantSentiment`, and `totalEventsAnalyzed` |
| `getById_returnsEntity` | `GET /FlexibilityForecasting/history/{id}` | Returns the forecast record when it exists, including `analyzedEventIds` |
| `getById_returnsNotFound` | `GET /FlexibilityForecasting/history/{id}` | Returns `404` when not found |
| `buildPrompt_returnsPromptString` | `POST /FlexibilityForecasting/build-prompt` | Returns `200` with a non-null `prompt` field containing `assetId`, `DISCHARGE`, and `gridCellId` |
| `persistForecast_returnsId` | `POST /FlexibilityForecasting/forecast` | Returns `200` with `forecastId` set from `LAST_INSERTED_ID` |
| `delete_withExistingId_returns204` | `DELETE /FlexibilityForecasting/history/{id}` | Returns `204 No Content` when the record exists |
| `delete_withNonExistingId_returns404` | `DELETE /FlexibilityForecasting/history/{id}` | Returns `404` when not found |

## SQL Verified

All stubs match the production SQL character-for-character. A mismatch causes Mockito to return `null`, which propagates as a `500` at runtime.

The `ForecastingResult` finder methods use `SELECT *` rather than an explicit column list — the stubs reflect this exactly.

```sql
SELECT * FROM ForecastingResult ORDER BY createdAt DESC
SELECT * FROM ForecastingResult WHERE id = ?
INSERT INTO ForecastingResult(successRate, dominantSentiment, totalEventsAnalyzed, analyzedEventIds, createdAt) VALUES (?,?,?,?,?)
DELETE FROM ForecastingResult WHERE id = ?
```

## How To Run

From the `FlexibilityForecasting` module root:

```bash
# Unit tests only
./mvnw clean test

# Integration tests (excluded by Surefire by default, must be named explicitly)
./mvnw clean test -Dtest=FlexibilityForecastingResourceIT

# Both in one command
./mvnw clean test -Dtest="FlexibilityForecastingResourceTest,FlexibilityForecastingResourceIT"
```
