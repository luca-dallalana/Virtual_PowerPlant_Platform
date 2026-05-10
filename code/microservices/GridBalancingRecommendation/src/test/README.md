# GridBalancingRecommendation Tests

This folder contains the tests for the `GridBalancingRecommendation` microservice. The code under test is the `BalancingRecommendation` model and the `GridBalancingRecommendationResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/GridBalancingRecommendationResourceTest.java`: unit tests for the `GridBalancingRecommendationResource` and the `BalancingRecommendation` domain behaviour.
- `src/test/java/org/acme/GridBalancingRecommendationResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `GridBalancingRecommendationResource`.
- `src/test/java/org/acme/GridBalancingRecommendationKafkaTest.java`: Kafka logic tests that use the SmallRye in-memory connector to verify emitted recommendation messages.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The unit tests also mock the `GridBalancingRecommendationService` to test the resource layer in isolation.

The Kafka test uses the real recommendation service with mocked persistence and verifies that a `RECOMMENDED` recommendation emits the expected JSON payload and Kafka key on the `grid-balancing-recommendation` channel.

## What Is Covered

### Unit tests: `GridBalancingRecommendationResourceTest`

These tests validate the behavior of the `BalancingRecommendation` model and `GridBalancingRecommendationResource` in isolation:

- `getAll_returnsList`: verifies that `getAll()` returns a list of `BalancingRecommendation` objects mapped from DB `Row` instances, including `id`, `sourceGridCellId`, `targetGridCellId`, `sourceNetLoadKw`, `targetHeadroomKw`, `overloadKw`, `transferableKw`, `thresholdPercent`, `status`, `rationale`, and `createdAt`.
- `getById_returnsEntity`: verifies that `getById(id)` returns the matching entity when a row exists and that the `Response` status is `200`.
- `getById_returnsNotFound`: verifies that `getById(id)` returns `404` when the prepared query returns no rows.
- `getBySource_returnsFiltered`: verifies that `getBySource(gridCellId)` returns only recommendations with the requested `sourceGridCellId` and that results are ordered by `createdAt` descending.
- `evaluate_withOverloadAndTarget_createsRECOMMENDED`: verifies that posting telemetry with an overloaded source grid cell and an available target grid cell creates a `RECOMMENDED` recommendation, persists it (mocked insert returns a generated id), sets expected fields (`status` = `RECOMMENDED`, `transferableKw` = `5.0`, `targetGridCellId` = `GRID_B`), and returns `200` with the created recommendation.
- `evaluate_withOverloadNoTarget_createsNO_TARGET`: verifies that posting telemetry with an overloaded source grid cell and no available target grid cell creates a `NO_TARGET` recommendation with `status` = `NO_TARGET`, `targetGridCellId` = `null`, and returns `200`.
- `evaluate_withNoOverload_returnsEmptyList`: verifies that posting telemetry with all grid cells below threshold returns `200` and an empty list of recommendations.
- `evaluate_withMultipleOverloads_createsMultipleRecommendations`: verifies that posting telemetry with multiple overloaded grid cells creates multiple recommendations and returns all of them.
- `evaluate_withEmptyData_returnsEmptyList`: verifies that posting empty telemetry and grid cell data returns `200` and an empty list of recommendations.
- `kafka_publish_recommended_recommendation_withSourceKey`: verifies the recommendation service publishes the expected JSON payload and uses the source grid cell id as the Kafka key.
- `create_withValidData_returns201`: verifies that posting a new `BalancingRecommendation` persists it (mocked insert returns generated id `123`), returns `201` and the Location header contains `/GridBalancingRecommendation/123`.
- `create_appliesDefaults`: verifies that posting a `BalancingRecommendation` without `createdAt`, `thresholdPercent`, and `status` applies default values (`createdAt` = current time, `thresholdPercent` = `0.9`, `status` = `MANUAL`).
- `update_withExistingId_returns204`: verifies that `PUT /GridBalancingRecommendation/{id}` with valid data returns `204 No Content`.
- `update_withNonExistingId_returns404`: verifies that `PUT /GridBalancingRecommendation/{id}` returns `404` when no record with that id exists.
- `delete_withExistingId_returns204`: verifies that `DELETE /GridBalancingRecommendation/{id}` returns `204 No Content` when a record is deleted.
- `delete_withNonExistingId_returns404`: verifies that `DELETE /GridBalancingRecommendation/{id}` returns `404` when no record with that id exists.

The unit tests also validate the exact SQL used by the model/resource methods:

- `SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation ORDER BY createdAt DESC`
- `SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE id = ?`
- `SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE sourceGridCellId = ? ORDER BY createdAt DESC`
- `INSERT INTO BalancingRecommendation(sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?)`
- `UPDATE BalancingRecommendation SET sourceGridCellId = ?, targetGridCellId = ?, sourceNetLoadKw = ?, targetHeadroomKw = ?, overloadKw = ?, transferableKw = ?, thresholdPercent = ?, status = ?, rationale = ?, createdAt = ? WHERE id = ?`
- `DELETE FROM BalancingRecommendation WHERE id = ?`

### Integration tests: `GridBalancingRecommendationResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured:

- `getAll_returnsList`: verifies `GET /GridBalancingRecommendation` returns `200` and a JSON array with all records.
- `getById_returnsEntity`: verifies `GET /GridBalancingRecommendation/{id}` returns `200` and one record when it exists.
- `getById_returnsNotFound`: verifies `GET /GridBalancingRecommendation/{id}` returns `404` when no record exists.
- `getBySource_returnsFiltered`: verifies `GET /GridBalancingRecommendation/source/{gridCellId}` returns `200` and records filtered by `sourceGridCellId`.
- `evaluate_withOverloadAndTarget_returnsRECOMMENDED`: verifies `POST /GridBalancingRecommendation/evaluate` returns `200` and a list containing one recommendation with `status` = `RECOMMENDED` and `targetGridCellId` = `GRID_B` when grid cells are overloaded and a target is available (mocked service provides recommendation).
- `evaluate_withOverloadNoTarget_returnsNO_TARGET`: verifies `POST /GridBalancingRecommendation/evaluate` returns `200` and a list containing one recommendation with `status` = `NO_TARGET` and `targetGridCellId` = `null` when all grid cells are overloaded (mocked service provides recommendation).
- `evaluate_withNoOverload_returnsEmptyList`: verifies `POST /GridBalancingRecommendation/evaluate` returns `200` and an empty list when all grid cells are within normal load (mocked service returns empty list).
- `create_withValidData_returns201`: verifies `POST /GridBalancingRecommendation` returns `201` and the Location header contains the new resource URI (mocked DB insert provides generated id).
- `create_appliesDefaults_returnsMANUAL`: verifies `POST /GridBalancingRecommendation` with partial data returns `201` and applies default values for missing fields (`status` = `MANUAL`, `thresholdPercent` = `0.9`).
- `update_withExistingId_returns204`: verifies `PUT /GridBalancingRecommendation/{id}` returns `204 No Content` when updating an existing record.
- `update_withNonExistingId_returns404`: verifies `PUT /GridBalancingRecommendation/{id}` returns `404` when the record does not exist.
- `delete_withExistingId_returns204`: verifies `DELETE /GridBalancingRecommendation/{id}` returns `204 No Content` when deleting an existing record.
- `delete_withNonExistingId_returns404`: verifies `DELETE /GridBalancingRecommendation/{id}` returns `404` when the record does not exist.

The integration tests mock the DB insert result to return a `LAST_INSERTED_ID` property used by the service to set the created entity `id`. The tests also mock the `GridBalancingRecommendationService` to provide predictable recommendation results.

## How To Run

From the `GridBalancingRecommendation` module root:

To run only the unit test class:

```bash
mvn test
```

Run only the Kafka logic test:
```bash
mvn test -Dtest=GridBalancingRecommendationKafkaTest
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.push=false
```
