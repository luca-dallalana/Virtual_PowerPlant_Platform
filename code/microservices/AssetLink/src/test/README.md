# AssetLink Tests

This folder contains the tests for the `AssetLink` microservice. The code under test is the `AssetLink` model and the `AssetLinkResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/AssetLinkTest.java`: unit tests for the `AssetLink` domain and persistence methods.
- `src/test/java/org/acme/AssetLinkResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `AssetLinkResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The unit test calls model methods directly; the IT test uses `@InjectMock`.

## What Is Covered

### Unit tests: `AssetLinkTest`

| Test | What it verifies |
|---|---|
| `from_mapsRowToAssetLink` | The private `from(Row)` method maps `id`, `idProsumer`, and `idUtilityOperator` correctly |
| `findAll_mapsRowsInOrder` | `findAll()` returns all rows mapped to `AssetLink` objects in ascending `id` order |
| `findById_returnsAssetLinkWhenFound` | `findById(id)` returns the matching entity when a row exists |
| `findById_returnsNullWhenNotFound` | `findById(id)` returns `null` when no row exists |
| `findById2_returnsAssetLinkWhenFound` | `findById2(idProsumer, idUtilityOperator)` returns the matching entity when a row exists |
| `findById2_returnsNullWhenNotFound` | `findById2(idProsumer, idUtilityOperator)` returns `null` when no row exists |
| `findByUtilityOperatorId_returnsMatchingLinks` | `findByUtilityOperatorId(id)` returns all links with matching `idUtilityOperator` |
| `findByUtilityOperatorId_returnsEmptyWhenNoLinks` | `findByUtilityOperatorId(id)` returns an empty list when no links exist |
| `findProsumerIdsByOperatorId_returnsList` | `findProsumerIdsByOperatorId(id)` returns the list of `idProsumer` values for a given operator |
| `findProsumerIdsByOperatorId_returnsEmptyWhenNoLinks` | `findProsumerIdsByOperatorId(id)` returns an empty list when no links exist |
| `findProsumerIdsByOperatorIds_returnsList` | `findProsumerIdsByOperatorIds(ids)` returns distinct prosumer IDs for a list of operator IDs using a dynamic `IN (?, ?)` query |
| `findProsumerIdsByOperatorIds_emptyInput_returnsEmpty` | `findProsumerIdsByOperatorIds([])` short-circuits without hitting the DB and returns an empty list |
| `save_returnsGeneratedIdWhenInsertSucceeds` | `save()` returns the generated id from `LAST_INSERTED_ID` after a successful insert |
| `save_returnsGeneratedIdWhenInsertSucceedsWithDifferentId` | Same insert path verified with a different generated id value |
| `delete_returnsTrueWhenRowIsRemoved` | `delete(id)` returns `true` when one row is deleted |
| `delete_returnsFalseWhenRowIsMissing` | `delete(id)` returns `false` when no row is deleted |
| `update_returnsTrueWhenRowIsUpdated` | `update(id, idProsumer, idUtilityOperator)` returns `true` when one row is updated |
| `update_returnsFalseWhenRowIsMissing` | `update(id, idProsumer, idUtilityOperator)` returns `false` when no row is updated |

### Integration tests: `AssetLinkResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured. `MySQLPool` is replaced with a Mockito mock via `@InjectMock`.

| Test | Endpoint | What it verifies |
|---|---|---|
| `get_returnsList` | `GET /AssetLink` | Returns a JSON array with all records including `id`, `idProsumer`, and `idUtilityOperator` |
| `getSingle_returnsEntity` | `GET /AssetLink/{id}` | Returns the record when it exists |
| `getSingle_returnsNotFound` | `GET /AssetLink/{id}` | Returns `404` when not found |
| `getDual_returnsEntity` | `GET /AssetLink/{idProsumer}/{idUtilityOperator}` | Returns the record matching the composite key |
| `getDual_returnsNotFound` | `GET /AssetLink/{idProsumer}/{idUtilityOperator}` | Returns `404` when the composite key has no match |
| `getByUtilityOperator_returnsList` | `GET /AssetLink/utilityoperator/{id}` | Returns all links for the given utility operator id |
| `getByUtilityOperator_returnsEmptyList` | `GET /AssetLink/utilityoperator/{id}` | Returns an empty array when no links exist |
| `getProsumerIdsByOperator_returnsList` | `GET /AssetLink/prosumerIds/by-operator/{id}` | Returns the list of prosumer IDs linked to the given operator |
| `getProsumerIdsByOperator_returnsEmptyList` | `GET /AssetLink/prosumerIds/by-operator/{id}` | Returns an empty array when no links exist |
| `getProsumerIdsByOperators_returnsList` | `POST /AssetLink/prosumerIds/by-operators` | Returns distinct prosumer IDs for a JSON array of operator IDs |
| `getProsumerIdsByOperators_emptyInput_returnsEmpty` | `POST /AssetLink/prosumerIds/by-operators` | Returns an empty array when the input list is empty |
| `create_returnsCreated` | `POST /AssetLink` | Returns `201 Created` after a successful insert |
| `delete_returnsNoContent` | `DELETE /AssetLink/{id}` | Returns `204 No Content` when the record exists |
| `delete_returnsNotFound` | `DELETE /AssetLink/{id}` | Returns `404` when the record does not exist |
| `update_returnsNoContent` | `PUT /AssetLink/{id}/{idProsumer}/{idUtilityOperator}` | Returns `204 No Content` when the record exists |
| `update_returnsNotFound` | `PUT /AssetLink/{id}/{idProsumer}/{idUtilityOperator}` | Returns `404` when the record does not exist |

## SQL Verified

All stubs match the production SQL character-for-character. A mismatch causes Mockito to return `null`, which propagates as a `500` at runtime.

```sql
SELECT id, idProsumer, idUtilityOperator  FROM AssetLink ORDER BY id ASC
SELECT id, idProsumer, idUtilityOperator  FROM AssetLink WHERE id = ?
SELECT id, idProsumer, idUtilityOperator FROM AssetLink WHERE idProsumer = ? AND idUtilityOperator = ?
SELECT id, idProsumer, idUtilityOperator FROM AssetLink WHERE idUtilityOperator = ?
SELECT idProsumer FROM AssetLink WHERE idUtilityOperator = ?
SELECT DISTINCT idProsumer FROM AssetLink WHERE idUtilityOperator IN (?, ?)
INSERT INTO AssetLink(idProsumer,idUtilityOperator) VALUES (?,?)
DELETE FROM AssetLink WHERE id = ?
UPDATE AssetLink SET idProsumer = ? , idUtilityOperator = ? WHERE id = ?
```

The `IN (?, ?)` placeholder count in the last SELECT is dynamic — it matches the number of operator IDs passed in the request body. The unit test uses a two-element list to verify the query shape.

## How To Run

From the `AssetLink` module root:

```bash
# Unit tests only
./mvnw clean test

# Integration tests (excluded by Surefire by default, must be named explicitly)
./mvnw clean test -Dtest=AssetLinkResourceIT

# Both in one command
./mvnw clean test -Dtest="AssetLinkTest,AssetLinkResourceIT"
```
