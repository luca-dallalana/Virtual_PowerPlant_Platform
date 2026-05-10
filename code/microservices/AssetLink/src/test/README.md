# AssetLink Tests

This folder contains the tests for the `AssetLink` microservice. The code under test is the `AssetLink` model and the `AssetLinkResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/AssetLinkTest.java`: unit tests for the `AssetLink` domain and persistence helper methods.
- `src/test/java/org/acme/AssetLinkResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `AssetLinkResource`.

Both test classes use Mockito to mock `MySQLPool`, so the tests do not need a live database. They verify the SQL strings, row mapping, and HTTP status codes produced by the resource layer.

## What Is Covered

### Unit tests: `AssetLinkTest`

These tests validate the behavior of the `AssetLink` class in isolation:

- `from_mapsRowToAssetLink`: verifies that a database `Row` is converted into an `AssetLink` with the expected `id`, `idProsumer`, and `idUtilityOperator` values.
- `findAll_mapsRowsInOrder`: verifies that `findAll` reads rows from `AssetLink` in ascending `id` order and maps each row correctly.
- `findById_returnsAssetLinkWhenFound`: verifies that `findById` returns the matching entity when a row exists.
- `findById_returnsNullWhenNotFound`: verifies that `findById` returns `null` when the query is empty.
- `findById2_returnsAssetLinkWhenFound`: verifies lookup by the `(idProsumer, idUtilityOperator)` pair.
- `save_returnsGeneratedIdWhenInsertSucceeds`: verifies that `save` returns the generated MySQL inserted id when the insert succeeds.
- `save_returnsGeneratedIdWhenInsertSucceedsWithDifferentId`: verifies the same insert path with a different generated id value.
- `delete_returnsTrueWhenRowIsRemoved`: verifies that `delete` returns `true` when one row is removed.
- `delete_returnsFalseWhenRowIsMissing`: verifies that `delete` returns `false` when nothing is deleted.
- `update_returnsTrueWhenRowIsUpdated`: verifies that `update` returns `true` when one row is updated.
- `update_returnsFalseWhenRowIsMissing`: verifies that `update` returns `false` when no row is updated.

The unit tests also validate the exact SQL used by the model methods:

- `SELECT id, idProsumer, idUtilityOperator  FROM AssetLink ORDER BY id ASC`
- `SELECT id, idProsumer, idUtilityOperator  FROM AssetLink WHERE id = ?`
- `SELECT id, idProsumer, idUtilityOperator FROM AssetLink WHERE idProsumer = ? AND idUtilityOperator = ?`
- `INSERT INTO AssetLink(idProsumer,idUtilityOperator) VALUES (?,?)`
- `DELETE FROM AssetLink WHERE id = ?`
- `UPDATE AssetLink SET idProsumer = ? , idUtilityOperator = ? WHERE id = ?`

### Integration tests: `AssetLinkResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured:

- `get_returnsList`: verifies `GET /AssetLink` returns a JSON array with all records.
- `getSingle_returnsEntity`: verifies `GET /AssetLink/{id}` returns one record when it exists.
- `getSingle_returnsNotFound`: verifies `GET /AssetLink/{id}` returns `404` when no record exists.
- `getDual_returnsEntity`: verifies `GET /AssetLink/{idProsumer}/{idUtilityOperator}` returns the matching record.
- `getDual_returnsNotFound`: verifies the dual-key lookup returns `404` when no record exists.
- `create_returnsCreated`: verifies `POST /AssetLink` returns `201 Created` after a successful insert.
- `delete_returnsNoContent`: verifies `DELETE /AssetLink/{id}` returns `204 No Content` when a row is removed.
- `delete_returnsNotFound`: verifies `DELETE /AssetLink/{id}` returns `404` when nothing is deleted.
- `update_returnsNoContent`: verifies `PUT /AssetLink/{id}/{idProsumer}/{idUtilityOperator}` returns `204 No Content` when a row is updated.
- `update_returnsNotFound`: verifies the update endpoint returns `404` when the target record does not exist.

## How To Run

From the `AssetLink` module root:

To run only the unit test class:

```bash
mvn test
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.push=false
```