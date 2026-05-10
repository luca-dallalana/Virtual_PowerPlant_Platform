# UtilityOperator Tests

This folder contains the tests for the `UtilityOperator` microservice. The code under test is the `UtilityOperator` and `GridCell` models and the `UtilityOperatorResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acm/UtilityOperatorResourceTest.java`: unit tests for the `UtilityOperatorResource`, `UtilityOperator`, and `GridCell` domain behaviour.
- `src/test/java/org/acm/UtilityOperatorResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `UtilityOperatorResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database.

## What Is Covered

### Unit tests: `UtilityOperatorResourceTest`

These tests validate the behavior of the `UtilityOperator` and `GridCell` models and `UtilityOperatorResource` in isolation:

#### UtilityOperator Tests

- `getUtilityOperators_returnsList`: verifies that `get()` returns a list of `UtilityOperator` objects mapped from DB `Row` instances, including `id`, `name`, and `location`.
- `getUtilityOperatorById_returnsEntity`: verifies that `getSingle(id)` returns the matching entity when a row exists and that the `Response` status is `200`.
- `getUtilityOperatorById_returnsNotFound`: verifies that `getSingle(id)` returns `404` when the prepared query returns no rows.
- `createUtilityOperator_returnsCreated`: verifies that `create(operator)` persists the operator (mocked insert returns a generated id), returns `201` with a `Location` header pointing to the created resource.
- `deleteUtilityOperator_returnsNoContent`: verifies that `delete(id)` returns `204 No Content` when the operator is found and deleted.
- `deleteUtilityOperator_returnsNotFound`: verifies that `delete(id)` returns `404` when no operator exists with the given id.
- `updateUtilityOperator_returnsNoContent`: verifies that `update(id, name, location)` returns `204 No Content` when the operator is found and updated.
- `updateUtilityOperator_returnsNotFound`: verifies that `update(id, name, location)` returns `404` when no operator exists with the given id.

#### GridCell Tests

- `getGridCells_returnsList`: verifies that `getAllGridCells()` returns a list of `GridCell` objects mapped from DB `Row` instances, including `gridCellId`, `utilityOperatorId`, `maxCapacity`, and `geographicBoundaries`.
- `getGridCellById_returnsEntity`: verifies that `getGridCell(gridCellId)` returns the matching entity when a row exists and that the `Response` status is `200`.
- `getGridCellById_returnsNotFound`: verifies that `getGridCell(gridCellId)` returns `404` when no grid cell exists.
- `getGridCellsByOperator_returnsList`: verifies that `getGridCellsByOperator(utilityOperatorId)` returns only grid cells with the requested `utilityOperatorId`.
- `getGridCellsByOperator_returnsEmptyList`: verifies that `getGridCellsByOperator(utilityOperatorId)` returns an empty list when no grid cells exist for the operator.
- `createGridCell_returnsCreated`: verifies that `createGridCell(gridCell)` persists the grid cell (mocked insert returns success), returns `201` with a `Location` header pointing to the created resource.
- `deleteGridCell_returnsNoContent`: verifies that `deleteGridCell(gridCellId)` first verifies the grid cell exists, then deletes it and returns `204 No Content`.
- `deleteGridCell_returnsNotFound`: verifies that `deleteGridCell(gridCellId)` returns `404` when no grid cell exists with the given id.
- `updateGridCell_returnsNoContent`: verifies that `updateGridCell(gridCellId, gridCell)` returns `204 No Content` when the grid cell is found and updated with new `utilityOperatorId`, `maxCapacity`, and `geographicBoundaries`.
- `updateGridCell_returnsNotFound`: verifies that `updateGridCell(gridCellId, gridCell)` returns `404` when no grid cell exists with the given id.
- `updateGridCellCapacity_returnsNoContent`: verifies that `updateGridCellCapacity(gridCellId, maxCapacity)` returns `204 No Content` when the grid cell is found and the capacity is updated.
- `updateGridCellCapacity_returnsNotFound`: verifies that `updateGridCellCapacity(gridCellId, maxCapacity)` returns `404` when no grid cell exists with the given id.

The unit tests also validate the exact SQL used by the model/resource methods:

**UtilityOperator SQL:**
- `SELECT id, name, location FROM UtilityOperator ORDER BY id ASC`
- `SELECT id, name, location FROM UtilityOperator WHERE id = ?`
- `INSERT INTO UtilityOperator(name,location) VALUES (?,?)`
- `DELETE FROM UtilityOperator WHERE id = ?`
- `UPDATE UtilityOperator SET name = ?, location = ? WHERE id = ?`

**GridCell SQL:**
- `SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell ORDER BY gridCellId ASC`
- `SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?`
- `SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE utilityOperatorId = ?`
- `INSERT INTO GridCell(gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES (?,?,?,?)`
- `DELETE FROM GridCell WHERE gridCellId = ?`
- `UPDATE GridCell SET utilityOperatorId = ?, maxCapacity = ?, geographicBoundaries = ? WHERE gridCellId = ?`
- `UPDATE GridCell SET maxCapacity = ? WHERE gridCellId = ?`

### Integration tests: `UtilityOperatorResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured:

#### UtilityOperator Endpoints

- `getUtilityOperators_returnsList`: verifies `GET /UtilityOperator` returns a JSON array with all operators including `id`, `name`, and `location`.
- `getUtilityOperatorById_returnsEntity`: verifies `GET /UtilityOperator/{id}` returns one operator when it exists.
- `getUtilityOperatorById_returnsNotFound`: verifies `GET /UtilityOperator/{id}` returns `404` when no operator exists.
- `createUtilityOperator_returnsCreated`: verifies `POST /UtilityOperator` with JSON body returns `201 Created` and a `Location` header with the new operator id (mocked DB insert provides generated id).
- `deleteUtilityOperator_returnsNoContent`: verifies `DELETE /UtilityOperator/{id}` returns `204 No Content` when the operator exists.
- `updateUtilityOperator_returnsNoContent`: verifies `PUT /UtilityOperator/{id}/{name}/{location}` returns `204 No Content` when the operator is found and updated.

#### GridCell Endpoints

- `getGridCells_returnsList`: verifies `GET /UtilityOperator/gridcell` returns a JSON array with all grid cells.
- `getGridCellById_returnsEntity`: verifies `GET /UtilityOperator/gridcell/{gridCellId}` returns one grid cell when it exists.
- `getGridCellsByOperator_returnsList`: verifies `GET /UtilityOperator/{utilityOperatorId}/gridcells` returns grid cells filtered by operator id.
- `getGridCellsByOperator_returnsEmptyList`: verifies `GET /UtilityOperator/{utilityOperatorId}/gridcells` returns an empty array when no grid cells exist.
- `createGridCell_returnsCreated`: verifies `POST /UtilityOperator/gridcell` with JSON body returns `201 Created` and a `Location` header with the grid cell id.
- `deleteGridCell_returnsNoContent`: verifies `DELETE /UtilityOperator/gridcell/{gridCellId}` returns `204 No Content` when the grid cell exists.
- `deleteGridCell_returnsNotFound`: verifies `DELETE /UtilityOperator/gridcell/{gridCellId}` returns `404` when no grid cell exists.
- `updateGridCell_returnsNoContent`: verifies `PUT /UtilityOperator/gridcell/{gridCellId}` with JSON body returns `204 No Content` when the grid cell is found and updated.
- `updateGridCellCapacity_returnsNoContent`: verifies `PUT /UtilityOperator/gridcell/{gridCellId}/capacity/{maxCapacity}` returns `204 No Content` when the grid cell is found and capacity is updated.

The integration tests mock the DB to simulate successful operations with appropriate row counts and inserted IDs.

## How To Run

From the `UtilityOperator` module root:

To run only the unit test class:

```bash
mvn test
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.push=false
```
