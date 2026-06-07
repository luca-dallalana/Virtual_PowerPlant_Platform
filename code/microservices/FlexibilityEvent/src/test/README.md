# FlexibilityEvent Tests

This folder contains the tests for the `FlexibilityEvent` microservice. The code under test is the `FlexibilityEvent` model and the `FlexibilityEventResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/FlexibilityEventResourceTest.java`: unit tests for the `FlexibilityEventResource` and the `FlexibilityEvent` domain behaviour.
- `src/test/java/org/acme/FlexibilityEventResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `FlexibilityEventResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The unit test injects the mock via reflection; the IT test uses `@InjectMock`.

## What Is Covered

### Unit tests: `FlexibilityEventResourceTest`

| Test | What it verifies |
|---|---|
| `getFlexibilityEvents_returnsList` | `get()` returns a list of `FlexibilityEvent` objects mapped from DB rows, including all fields (`id`, `assetId`, `prosumerId`, `eventType`, `soc_percent`, `soh_percent`, `recommendedAction`, `marketPriceLevel`, `gridCellId`, `timestamp`) |
| `getFlexibilityEventById_returnsEntity` | `getSingle(id)` returns the matching entity with status `200` |
| `getFlexibilityEventById_returnsNotFound` | `getSingle(id)` returns `404` when no row exists |
| `getFlexibilityEventsByAssetId_returnsList` | `getByAsset(assetId)` returns only events with the requested `assetId`, ordered by `timestamp` descending |
| `getFlexibilityEventsByEventType_returnsList` | `getByType(eventType)` returns only events with the requested `eventType`, ordered by `timestamp` descending |
| `getLogsByMinutes_returnsList` | `getLogsByMinutes(minutes)` returns events within the time window using a `timestamp >= ? AND timestamp <= ?` query |
| `getLogsByMinutes_returnsEmptyList` | `getLogsByMinutes(minutes)` returns an empty list when no events fall within the window |
| `saveBatch_persistsEventsAndReturnsWithIds` | `saveBatch(events)` inserts each event, reads back `LAST_INSERTED_ID`, and returns the list with `id` populated |
| `saveBatch_emptyList_returnsEmptyResponse` | `saveBatch([])` short-circuits without hitting the DB and returns an empty list with status `200` |
| `saveBatch_nullTimestamp_getsAutoPopulated` | `saveBatch` sets `timestamp` to `LocalDateTime.now()` on events that arrive with a `null` timestamp |

### Integration tests: `FlexibilityEventResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured. `MySQLPool` is replaced with a Mockito mock via `@InjectMock`.

| Test | Endpoint | What it verifies |
|---|---|---|
| `getFlexibilityEvents_returnsList` | `GET /FlexibilityEvent` | Returns a JSON array with all fields for each event |
| `getFlexibilityEventById_returnsEntity` | `GET /FlexibilityEvent/{id}` | Returns the event when it exists |
| `getFlexibilityEventById_returnsNotFound` | `GET /FlexibilityEvent/{id}` | Returns `404` when not found |
| `getFlexibilityEventsByAssetId_returnsList` | `GET /FlexibilityEvent/asset/{assetId}` | Returns events filtered by `assetId` |
| `getFlexibilityEventsByEventType_returnsList` | `GET /FlexibilityEvent/type/{eventType}` | Returns events filtered by `eventType` |
| `getLogsByMinutes_returnsList` | `GET /FlexibilityEvent/logs/{minutes}` | Returns events within the last N minutes |
| `getLogsByMinutes_returnsEmptyList` | `GET /FlexibilityEvent/logs/{minutes}` | Returns an empty array when no events fall in the window |
| `saveBatch_persistsEventsAndReturnsWithIds` | `POST /FlexibilityEvent/save` | Returns `200` with the saved events list; each event has `id` set from `LAST_INSERTED_ID` |
| `saveBatch_emptyList_returnsEmptyResponse` | `POST /FlexibilityEvent/save` | Returns `200` with an empty array when the request body is an empty list |

## SQL Verified

All stubs match the production SQL character-for-character. A mismatch causes Mockito to return `null`, which propagates as a `500` at runtime.

```sql
SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, recommendedAction, marketPriceLevel, gridCellId, timestamp FROM FlexibilityEvent ORDER BY timestamp DESC
SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, recommendedAction, marketPriceLevel, gridCellId, timestamp FROM FlexibilityEvent WHERE id = ?
SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, recommendedAction, marketPriceLevel, gridCellId, timestamp FROM FlexibilityEvent WHERE assetId = ? ORDER BY timestamp DESC
SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, recommendedAction, marketPriceLevel, gridCellId, timestamp FROM FlexibilityEvent WHERE eventType = ? ORDER BY timestamp DESC
SELECT id, assetId, prosumerId, eventType, soc_percent, soh_percent, recommendedAction, marketPriceLevel, gridCellId, timestamp FROM FlexibilityEvent WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC
INSERT INTO FlexibilityEvent(assetId, prosumerId, eventType, soc_percent, soh_percent, recommendedAction, marketPriceLevel, gridCellId, timestamp) VALUES (?,?,?,?,?,?,?,?,?)
```

## How To Run

From the `FlexibilityEvent` module root:

```bash
# Unit tests only
./mvnw clean test

# Integration tests (excluded by Surefire by default, must be named explicitly)
./mvnw clean test -Dtest=FlexibilityEventResourceIT

# Both in one command
./mvnw clean test -Dtest="FlexibilityEventResourceTest,FlexibilityEventResourceIT"
```
