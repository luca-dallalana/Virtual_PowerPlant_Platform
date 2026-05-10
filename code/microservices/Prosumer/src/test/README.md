# Prosumer Tests

This folder contains the tests for the `Prosumer` microservice. The code under test is the `Prosumer` model, the `Asset` model, and the `ProsumerResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acm/ProsumerResourceTest.java`: unit tests for the `ProsumerResource`, `Prosumer`, and `Asset` behavior.
- `src/test/java/org/acm/ProsumerResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `ProsumerResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The tests also mock Vert.x `Row`, `RowSet`, `Query`, and `PreparedQuery` objects to isolate the SQL and response handling logic.

## What Is Covered

### Unit tests: `ProsumerResourceTest`

These tests validate the behavior of the `ProsumerResource` endpoints and the `Prosumer` and `Asset` domain logic in isolation:

#### Prosumer Tests

- `getProsumers_returnsList`: verifies that `get()` returns a list of `Prosumer` objects mapped from DB `Row` instances, including `id`, `name`, `FiscalNumber`, and `location`.
- `getProsumerById_returnsEntity`: verifies that `getSingle(id)` returns the matching entity when a row exists and that the `Response` status is `200`.
- `getProsumerById_returnsNotFound`: verifies that `getSingle(id)` returns `404` when the prepared query returns no rows.
- `createProsumer_returnsCreated`: verifies that posting a `Prosumer` creates the entity, returns `201 Created`, and sets a non-empty `Location` header under `/Prosumer/`.
- `deleteProsumer_returnsNoContent`: verifies that deleting an existing `Prosumer` returns `204 No Content`.
- `deleteProsumer_returnsNotFound`: verifies that deleting a missing `Prosumer` returns `404`.
- `updateProsumer_returnsNoContent`: verifies that updating an existing `Prosumer` returns `204 No Content`.
- `updateProsumer_returnsNotFound`: verifies that updating a missing `Prosumer` returns `404`.

#### Asset Tests

- `getAssets_returnsList`: verifies that `getAssets(prosumerId)` returns the assets mapped from DB rows and filtered by `prosumerId`.
- `getAssets_returnsEmptyList`: verifies that `getAssets(prosumerId)` returns an empty list when no rows are found.
- `createAsset_returnsCreated`: verifies that posting an `Asset` under a `Prosumer` returns `201 Created` and sets the `Location` header to `/Prosumer/{prosumerId}/assets/{assetId}`.
- `deleteAsset_returnsNoContent`: verifies that deleting an existing `Asset` returns `204 No Content`.
- `deleteAsset_returnsNotFound`: verifies that deleting a missing `Asset` returns `404`.
- `updateAssetStatus_returnsNoContent`: verifies that updating an existing asset status returns `204 No Content`.
- `updateAssetStatus_returnsNotFound`: verifies that updating a missing asset status returns `404`.

The unit tests also validate the exact SQL used by the model/resource methods:

**Prosumer SQL:**
- `SELECT id, name, FiscalNumber , location FROM Prosumer ORDER BY id ASC`
- `SELECT id, name, FiscalNumber , location FROM Prosumer WHERE id = ?`
- `INSERT INTO Prosumer(name,FiscalNumber,location) VALUES (?,?,?)`
- `DELETE FROM Prosumer WHERE id = ?`
- `UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?`

**Asset SQL:**
- `SELECT assetId, prosumerId, assetType, model, status FROM Asset WHERE prosumerId = ?`
- `INSERT INTO Asset(assetId, prosumerId, assetType, model, status) VALUES (?,?,?,?,?)`
- `DELETE FROM Asset WHERE assetId = ?`
- `UPDATE Asset SET status = ? WHERE assetId = ?`

### Integration tests: `ProsumerResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured:

#### Prosumer Endpoints

- `getProsumers_returnsList`: verifies `GET /Prosumer` returns a JSON array with all prosumers including `id`, `name`, `FiscalNumber`, and `location`.
- `getProsumerById_returnsEntity`: verifies `GET /Prosumer/{id}` returns one prosumer when it exists.
- `getProsumerById_returnsNotFound`: verifies `GET /Prosumer/{id}` returns `404` when no prosumer exists.
- `createProsumer_returnsCreated`: verifies `POST /Prosumer` with JSON body returns `201 Created` and a `Location` header with the new prosumer id.
- `deleteProsumer_returnsNoContent`: verifies `DELETE /Prosumer/{id}` returns `204 No Content` when the prosumer exists.
- `deleteProsumer_returnsNotFound`: verifies `DELETE /Prosumer/{id}` returns `404` when no prosumer exists.
- `updateProsumer_returnsNoContent`: verifies `PUT /Prosumer/{id}/{name}/{FiscalNumber}/{location}` returns `204 No Content` when the prosumer is found and updated.
- `updateProsumer_returnsNotFound`: verifies the same update path returns `404` when the prosumer does not exist.

#### Asset Endpoints

- `getAssets_returnsList`: verifies `GET /Prosumer/{prosumerId}/assets` returns assets with matching `prosumerId`.
- `getAssets_returnsEmptyList`: verifies `GET /Prosumer/{prosumerId}/assets` returns an empty array when no assets exist.
- `createAsset_returnsCreated`: verifies `POST /Prosumer/{prosumerId}/assets` with JSON body returns `201 Created` and the expected `Location` header pointing to `/Prosumer/{prosumerId}/assets/{assetId}`.
- `deleteAsset_returnsNoContent`: verifies `DELETE /Prosumer/{prosumerId}/assets/{assetId}` returns `204 No Content`.
- `deleteAsset_returnsNotFound`: verifies the same delete path returns `404` when the asset is missing.
- `updateAssetStatus_returnsNoContent`: verifies `PUT /Prosumer/{prosumerId}/assets/{assetId}/status/{status}` returns `204 No Content`.
- `updateAssetStatus_returnsNotFound`: verifies the same status update returns `404` when the asset is missing.

## How To Run

From the `Prosumer` module root:

To run only the unit test class:

```bash
mvn test
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.push=false
```

