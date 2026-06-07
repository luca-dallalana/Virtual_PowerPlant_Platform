# UtilityOperator Tests

This folder contains the tests for the `UtilityOperator` microservice. The code under test is the `UtilityOperator` and `GridCell` models and the `UtilityOperatorResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acm/UtilityOperatorResourceTest.java`: unit tests for the `UtilityOperatorResource`, `UtilityOperator`, and `GridCell` domain behaviour.
- `src/test/java/org/acm/UtilityOperatorResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `UtilityOperatorResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The unit test injects the mock via reflection; the IT test uses `@InjectMock`.

## What Is Covered

### Unit tests: `UtilityOperatorResourceTest`

#### UtilityOperator tests

| Test | What it verifies |
|---|---|
| `getUtilityOperators_returnsList` | `get()` returns a list of `UtilityOperator` objects mapped from DB rows, including `id`, `name`, and `location` |
| `getUtilityOperatorById_returnsEntity` | `getSingle(id)` returns the matching entity with status `200` |
| `getUtilityOperatorById_returnsNotFound` | `getSingle(id)` returns `404` when no row exists |
| `createUtilityOperator_returnsCreated` | `create()` returns `201 Created` with a `Location` header pointing to the new resource |
| `deleteUtilityOperator_returnsNoContent` | `delete()` on an existing operator returns `204 No Content` |
| `deleteUtilityOperator_returnsNotFound` | `delete()` on a missing operator returns `404` |
| `updateUtilityOperator_returnsNoContent` | `update()` on an existing operator returns `204 No Content` |
| `updateUtilityOperator_returnsNotFound` | `update()` on a missing operator returns `404` |

#### GridCell tests

| Test | What it verifies |
|---|---|
| `getGridCells_returnsList` | `getAllGridCells()` returns all grid cells mapped from DB rows, including `gridCellId`, `utilityOperatorId`, `maxCapacity`, and `geographicBoundaries` |
| `getGridCellById_returnsEntity` | `getGridCell(gridCellId)` returns the matching entity with status `200` |
| `getGridCellById_returnsNotFound` | `getGridCell(gridCellId)` returns `404` when no row exists |
| `getGridCellsByOperator_returnsList` | `getGridCellsByOperator(id)` returns cells filtered by `utilityOperatorId` |
| `getGridCellsByOperator_returnsEmptyList` | `getGridCellsByOperator(id)` returns an empty list when no cells exist for the operator |
| `createGridCell_returnsCreated` | `createGridCell()` returns `201 Created` with `Location` pointing to `/UtilityOperator/gridcells/{gridCellId}` |
| `deleteGridCell_returnsNoContent` | `deleteGridCell()` checks existence first, then deletes and returns `204 No Content` |
| `deleteGridCell_returnsNotFound` | `deleteGridCell()` returns `404` when the cell does not exist |
| `updateGridCell_returnsNoContent` | `updateGridCell()` returns `204 No Content` when the cell is found and updated |
| `updateGridCell_returnsNotFound` | `updateGridCell()` returns `404` when the cell does not exist |
| `updateGridCellCapacity_returnsNoContent` | `updateGridCellCapacity()` returns `204 No Content` when the cell exists |
| `updateGridCellCapacity_returnsNotFound` | `updateGridCellCapacity()` returns `404` when the cell does not exist |
| `getNeighbourGridCells_returnsList` | `getNeighbourGridCells(gridCellId)` returns all grid cells sharing the same `geographicBoundaries` excluding the queried cell |
| `getNeighbourGridCells_returnsEmpty` | `getNeighbourGridCells(gridCellId)` returns an empty list when no neighbours exist |

### Integration tests: `UtilityOperatorResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured. `MySQLPool` is replaced with a Mockito mock via `@InjectMock`.

#### UtilityOperator endpoints

| Test | Endpoint | What it verifies |
|---|---|---|
| `getUtilityOperators_returnsList` | `GET /UtilityOperator` | Returns a JSON array with `id`, `name`, and `location` |
| `getUtilityOperatorById_returnsEntity` | `GET /UtilityOperator/{id}` | Returns the operator when it exists |
| `getUtilityOperatorById_returnsNotFound` | `GET /UtilityOperator/{id}` | Returns `404` when not found |
| `createUtilityOperator_returnsCreated` | `POST /UtilityOperator` | Returns `201 Created` with a `Location` header ending in `/UtilityOperator/{id}` |
| `deleteUtilityOperator_returnsNoContent` | `DELETE /UtilityOperator/{id}` | Returns `204 No Content` when the operator exists |
| `updateUtilityOperator_returnsNoContent` | `PUT /UtilityOperator/{id}/{name}/{location}` | Returns `204 No Content` when found |

#### GridCell endpoints

| Test | Endpoint | What it verifies |
|---|---|---|
| `getGridCells_returnsList` | `GET /UtilityOperator/gridcells` | Returns a JSON array with all grid cells |
| `getGridCellById_returnsEntity` | `GET /UtilityOperator/gridcells/{gridCellId}` | Returns the grid cell when it exists |
| `getGridCellsByOperator_returnsList` | `GET /UtilityOperator/{id}/gridcells` | Returns grid cells filtered by operator id |
| `getGridCellsByOperator_returnsEmptyList` | `GET /UtilityOperator/{id}/gridcells` | Returns an empty array when none exist |
| `createGridCell_returnsCreated` | `POST /UtilityOperator/gridcells` | Returns `201 Created` with `Location` ending in `/UtilityOperator/gridcells/{gridCellId}` |
| `deleteGridCell_returnsNoContent` | `DELETE /UtilityOperator/gridcells/{gridCellId}` | Returns `204 No Content` when the cell exists |
| `deleteGridCell_returnsNotFound` | `DELETE /UtilityOperator/gridcells/{gridCellId}` | Returns `404` when not found |
| `updateGridCell_returnsNoContent` | `PUT /UtilityOperator/gridcells/{gridCellId}` | Returns `204 No Content` when found |
| `updateGridCellCapacity_returnsNoContent` | `PUT /UtilityOperator/gridcells/{gridCellId}/capacity/{maxCapacity}` | Returns `204 No Content` when found |
| `getNeighbourGridCells_returnsList` | `GET /UtilityOperator/gridcells/{gridCellId}/neighbours` | Returns all grid cells sharing the same `geographicBoundaries`, excluding the queried cell |
| `getNeighbourGridCells_returnsEmpty` | `GET /UtilityOperator/gridcells/{gridCellId}/neighbours` | Returns an empty array when no neighbours exist |

## SQL Verified

All stubs match the production SQL character-for-character. A mismatch causes Mockito to return `null`, which propagates as a `500` at runtime.

**UtilityOperator:**
```sql
SELECT id, name, location FROM UtilityOperator ORDER BY id ASC
SELECT id, name, location FROM UtilityOperator WHERE id = ?
INSERT INTO UtilityOperator(name,location) VALUES (?,?)
DELETE FROM UtilityOperator WHERE id = ?
UPDATE UtilityOperator SET name = ?, location = ? WHERE id = ?
```

**GridCell:**
```sql
SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell ORDER BY gridCellId ASC
SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?
SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE utilityOperatorId = ?
SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE geographicBoundaries = (SELECT geographicBoundaries FROM GridCell WHERE gridCellId = ?) AND gridCellId != ?
INSERT INTO GridCell(gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES (?,?,?,?)
DELETE FROM GridCell WHERE gridCellId = ?
UPDATE GridCell SET utilityOperatorId = ?, maxCapacity = ?, geographicBoundaries = ? WHERE gridCellId = ?
UPDATE GridCell SET maxCapacity = ? WHERE gridCellId = ?
```

## How To Run

From the `UtilityOperator` module root:

```bash
# Unit tests only
./mvnw clean test

# Integration tests (excluded by Surefire by default, must be named explicitly)
./mvnw clean test -Dtest=UtilityOperatorResourceIT

# Both in one command
./mvnw clean test -Dtest="UtilityOperatorResourceTest,UtilityOperatorResourceIT"
```
