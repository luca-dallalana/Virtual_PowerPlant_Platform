# GridBalancingRecommendation Tests

This folder contains the tests for the `GridBalancingRecommendation` microservice. The code under test is the `BalancingRecommendation` entity, the `GridBalancingRecommendationResource` REST API, and the `GridBalancingRecommendationService` business logic.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/GridBalancingRecommendationResourceTest.java`: unit tests for `GridBalancingRecommendationResource` with mocked `MySQLPool` and mocked `GridBalancingRecommendationService`.
- `src/test/java/org/acme/GridBalancingRecommendationResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `GridBalancingRecommendationResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The unit test injects mocks via reflection; the IT test uses `@InjectMock`.

## What Is Covered

### Unit tests: `GridBalancingRecommendationResourceTest`

#### CRUD tests

| Test | What it verifies |
|---|---|
| `getAll_returnsList` | `getAll()` returns a list of `BalancingRecommendation` objects mapped from DB rows, including `id`, `assetId`, `action`, `fromCell`, `toCell`, `createdAt`, `cellContext`, `socPercent`, `isCharging`, and `assetType` |
| `getById_returnsEntity` | `getById(id)` returns the matching entity with status `200` |
| `getById_returnsNotFound` | `getById(id)` returns `404` when no row exists |
| `getBySource_returnsFiltered` | `getBySource(fromCell)` returns only recommendations with the requested source cell, ordered by `createdAt` descending |
| `getByMinutes_returnsList` | `getByMinutes(n)` returns recommendations within the time window using a `createdAt >= ? AND createdAt <= ?` query |
| `getByMinutes_returnsEmptyList` | `getByMinutes(n)` returns an empty list when no recommendations fall within the window |
| `create_withValidData_returns201` | `create()` returns `201 Created` with a `Location` header containing the generated id |
| `create_setsCreatedAtWhenNull` | `create()` sets `createdAt` to `LocalDateTime.now()` when the field is null on the incoming entity |
| `update_withExistingId_returns204` | `update(id, entity)` returns `204 No Content` when the id exists |
| `update_withNonExistingId_returns404` | `update(id, entity)` returns `404` when the id does not exist |
| `delete_withExistingId_returns204` | `delete(id)` returns `204 No Content` when the id exists |
| `delete_withNonExistingId_returns404` | `delete(id)` returns `404` when the id does not exist |

#### Metrics tests

| Test | What it verifies |
|---|---|
| `computeMetrics_singleCell_returnsMetrics` | `computeMetrics(request)` with `gridCell` set delegates to `computeSingleCellMetrics` and returns `200` with `gridCellId` and `netLoad` |
| `computeMetrics_multiCell_returnsList` | `computeMetrics(request)` with `neighbourCells` set delegates to `computeMultiCellMetrics` and returns `200` |
| `computeMetrics_emptyRequest_returns400` | `computeMetrics(request)` with neither `gridCell` nor `neighbourCells` returns `400` |

#### Save test

| Test | What it verifies |
|---|---|
| `saveRecommendations_returnsSavedList` | `saveRecommendations(dtos)` delegates to the service and returns `200` with the saved recommendation list |

### Integration tests: `GridBalancingRecommendationResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured. `MySQLPool` is replaced with a Mockito mock via `@InjectMock`.

#### CRUD endpoints

| Test | Endpoint | What it verifies |
|---|---|---|
| `getAll_returnsList` | `GET /GridBalancingRecommendation` | Returns a JSON array with all fields for each recommendation |
| `getById_returnsEntity` | `GET /GridBalancingRecommendation/{id}` | Returns the recommendation when it exists |
| `getById_returnsNotFound` | `GET /GridBalancingRecommendation/{id}` | Returns `404` when not found |
| `getBySource_returnsFiltered` | `GET /GridBalancingRecommendation/source/{fromCell}` | Returns recommendations filtered by source cell |
| `getByMinutes_returnsList` | `GET /GridBalancingRecommendation/recommendations/{minutes}` | Returns recommendations within the last N minutes |
| `create_withValidData_returns201` | `POST /GridBalancingRecommendation` | Returns `201 Created` with a `Location` header containing the generated id |
| `update_withExistingId_returns204` | `PUT /GridBalancingRecommendation/{id}` | Returns `204 No Content` when the id exists |
| `update_withNonExistingId_returns404` | `PUT /GridBalancingRecommendation/{id}` | Returns `404` when not found |
| `delete_withExistingId_returns204` | `DELETE /GridBalancingRecommendation/{id}` | Returns `204 No Content` when the id exists |
| `delete_withNonExistingId_returns404` | `DELETE /GridBalancingRecommendation/{id}` | Returns `404` when not found |

#### Metrics endpoint

| Test | Endpoint | What it verifies |
|---|---|---|
| `computeMetrics_singleCell_returns200` | `POST /GridBalancingRecommendation/metrics` | Returns `200` with `gridCellId` when `gridCell` is present in the request |
| `computeMetrics_emptyRequest_returns400` | `POST /GridBalancingRecommendation/metrics` | Returns `400` when neither `gridCell` nor `neighbourCells` is provided |

#### Save endpoint

| Test | Endpoint | What it verifies |
|---|---|---|
| `saveRecommendations_returns200` | `POST /GridBalancingRecommendation/save` | Returns `200` with the persisted recommendations |

## SQL Verified

All stubs match the production SQL character-for-character. A mismatch causes Mockito to return `null`, which propagates as a `500` at runtime.

The column list used in all SELECT queries comes from the `SELECT_COLS` constant defined in the resource.

```sql
SELECT id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType FROM BalancingRecommendation ORDER BY createdAt DESC
SELECT id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType FROM BalancingRecommendation WHERE id = ?
SELECT id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType FROM BalancingRecommendation WHERE fromCell = ? ORDER BY createdAt DESC
SELECT id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType FROM BalancingRecommendation WHERE createdAt >= ? AND createdAt <= ? ORDER BY createdAt DESC
INSERT INTO BalancingRecommendation (assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType) VALUES (?,?,?,?,?,?,?,?,?)
UPDATE BalancingRecommendation SET assetId = ?, action = ?, fromCell = ?, toCell = ?, createdAt = ?, cellContext = ?, socPercent = ?, isCharging = ?, assetType = ? WHERE id = ?
DELETE FROM BalancingRecommendation WHERE id = ?
```

## How To Run

From the `GridBalancingRecommendation` module root:

```bash
# Unit tests only
./mvnw clean test

# Integration tests (excluded by Surefire by default, must be named explicitly)
./mvnw clean test -Dtest=GridBalancingRecommendationResourceIT

# Both in one command
./mvnw clean test -Dtest="GridBalancingRecommendationResourceTest,GridBalancingRecommendationResourceIT"
```
