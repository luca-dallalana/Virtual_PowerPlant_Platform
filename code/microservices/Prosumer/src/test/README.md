# Prosumer Tests

This folder contains the tests for the `Prosumer` microservice. The code under test is the `Prosumer` model, the `Asset` model, and the `ProsumerResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acm/ProsumerResourceTest.java`: unit tests for the `ProsumerResource`, `Prosumer`, and `Asset` behavior.
- `src/test/java/org/acm/ProsumerResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `ProsumerResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The tests also mock Vert.x `Row`, `RowSet`, `Query`, and `PreparedQuery` objects to isolate the SQL and response handling logic.

## What Is Covered

### Unit tests: `ProsumerResourceTest`

These tests validate the behavior of the `ProsumerResource` endpoints and the `Prosumer` and `Asset` domain logic in isolation.

#### Prosumer tests

| Test | What it verifies |
|---|---|
| `getProsumers_returnsList` | `get()` returns a list of `Prosumer` objects mapped from DB rows, including `id`, `name`, `FiscalNumber`, and `location` |
| `getProsumerById_returnsEntity` | `getSingle(id)` returns the matching entity with status `200` |
| `getProsumerById_returnsNotFound` | `getSingle(id)` returns `404` when the query returns no rows |
| `createProsumer_returnsCreated` | `create()` returns `201 Created` with a `Location` header under `/Prosumer/` |
| `deleteProsumer_returnsNoContent` | `delete()` on an existing prosumer returns `204 No Content` |
| `deleteProsumer_returnsNotFound` | `delete()` on a missing prosumer returns `404` |
| `updateProsumer_returnsNoContent` | `update()` on an existing prosumer returns `204 No Content` |
| `updateProsumer_returnsNotFound` | `update()` on a missing prosumer returns `404` |

#### Asset tests

| Test | What it verifies |
|---|---|
| `getAssets_returnsList` | `getAssets(prosumerId)` returns assets mapped from DB rows filtered by `prosumerId` |
| `getAssets_returnsEmptyList` | `getAssets(prosumerId)` returns an empty list when no rows are found |
| `createAsset_returnsCreated` | `createAsset()` returns `201 Created` with `Location` pointing to `/Prosumer/{prosumerId}/assets/{assetId}` |
| `deleteAsset_returnsNoContent` | `deleteAsset()` on an existing asset returns `204 No Content` |
| `deleteAsset_returnsNotFound` | `deleteAsset()` on a missing asset returns `404` |
| `updateAssetStatus_returnsNoContent` | `updateAssetStatus()` on an existing asset returns `204 No Content` |
| `updateAssetStatus_returnsNotFound` | `updateAssetStatus()` on a missing asset returns `404` |
| `getAllAssets_returnsList` | `getAllAssets()` returns all assets across all prosumers ordered by `assetId` |
| `getAllAssets_returnsEmptyList` | `getAllAssets()` returns an empty list when no assets exist |
| `getActiveAssetsByType_returnsList` | `getActiveAssetsByType(type)` returns active assets filtered by type and status |
| `getActiveAssetsByType_returnsEmptyList` | `getActiveAssetsByType(type)` returns an empty list when no matching assets exist |
| `getActiveAssetIdsByProsumers_returnsList` | `getActiveAssetIdsByProsumers(ids)` returns asset IDs for a list of prosumer IDs using a dynamic `IN (?,?)` query |
| `getActiveAssetIdsByProsumers_emptyInput_returnsEmpty` | `getActiveAssetIdsByProsumers([])` short-circuits without hitting the DB and returns an empty list |

### Integration tests: `ProsumerResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured. `MySQLPool` is replaced with a Mockito mock via `@InjectMock`.

#### Prosumer endpoints

| Test | Endpoint | What it verifies |
|---|---|---|
| `getProsumers_returnsList` | `GET /Prosumer` | Returns a JSON array with `id`, `name`, `FiscalNumber`, and `location` |
| `getProsumerById_returnsEntity` | `GET /Prosumer/{id}` | Returns the prosumer when it exists |
| `getProsumerById_returnsNotFound` | `GET /Prosumer/{id}` | Returns `404` when not found |
| `createProsumer_returnsCreated` | `POST /Prosumer` | Returns `201 Created` with a `Location` header ending in `/Prosumer/{id}` |
| `deleteProsumer_returnsNoContent` | `DELETE /Prosumer/{id}` | Returns `204 No Content` when the prosumer exists |
| `deleteProsumer_returnsNotFound` | `DELETE /Prosumer/{id}` | Returns `404` when not found |
| `updateProsumer_returnsNoContent` | `PUT /Prosumer/{id}/{name}/{FiscalNumber}/{location}` | Returns `204 No Content` when found |
| `updateProsumer_returnsNotFound` | `PUT /Prosumer/{id}/{name}/{FiscalNumber}/{location}` | Returns `404` when not found |

#### Asset endpoints

| Test | Endpoint | What it verifies |
|---|---|---|
| `getAssets_returnsList` | `GET /Prosumer/{prosumerId}/assets` | Returns assets with matching `prosumerId` |
| `getAssets_returnsEmptyList` | `GET /Prosumer/{prosumerId}/assets` | Returns empty array when no assets exist |
| `createAsset_returnsCreated` | `POST /Prosumer/{prosumerId}/assets` | Returns `201 Created` with `Location` ending in `/Prosumer/{prosumerId}/assets/{assetId}` |
| `deleteAsset_returnsNoContent` | `DELETE /Prosumer/{prosumerId}/assets/{assetId}` | Returns `204 No Content` |
| `deleteAsset_returnsNotFound` | `DELETE /Prosumer/{prosumerId}/assets/{assetId}` | Returns `404` when missing |
| `updateAssetStatus_returnsNoContent` | `PUT /Prosumer/{prosumerId}/assets/{assetId}/status/{status}` | Returns `204 No Content` |
| `updateAssetStatus_returnsNotFound` | `PUT /Prosumer/{prosumerId}/assets/{assetId}/status/{status}` | Returns `404` when missing |
| `getActiveAssetIdsByProsumers_returnsList` | `POST /Prosumer/assets/active/by-prosumers` | Returns a list of active asset IDs for the given prosumer IDs |
| `getActiveAssetIdsByProsumers_emptyInput_returnsEmpty` | `POST /Prosumer/assets/active/by-prosumers` | Returns an empty array when the input list is empty |

## SQL Verified

All stubs match the production SQL character-for-character. A mismatch causes Mockito to return `null`, which propagates as a `500` at runtime.

**Prosumer:**
```sql
SELECT id, name, FiscalNumber , location FROM Prosumer ORDER BY id ASC
SELECT id, name, FiscalNumber , location FROM Prosumer WHERE id = ?
INSERT INTO Prosumer(name,FiscalNumber,location) VALUES (?,?,?)
DELETE FROM Prosumer WHERE id = ?
UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?
```

**Asset:**
```sql
SELECT assetId, prosumerId, assetType, model, status FROM Asset WHERE prosumerId = ?
SELECT assetId, prosumerId, assetType, model, status FROM Asset ORDER BY assetId ASC
SELECT assetId, prosumerId, assetType FROM Asset WHERE assetType = ? AND status = ?
SELECT assetId FROM Asset WHERE status = 'ACTIVE' AND prosumerId IN (?, ?)
INSERT INTO Asset(assetId, prosumerId, assetType, model, status) VALUES (?,?,?,?,?)
DELETE FROM Asset WHERE assetId = ?
UPDATE Asset SET status = ? WHERE assetId = ?
```

## How To Run

From the `Prosumer` module root:

```bash
# Unit tests only
./mvnw clean test

# Integration tests (excluded by Surefire by default, must be named explicitly)
./mvnw clean test -Dtest=ProsumerResourceIT

# Both in one command
./mvnw clean test -Dtest="ProsumerResourceTest,ProsumerResourceIT"
```
