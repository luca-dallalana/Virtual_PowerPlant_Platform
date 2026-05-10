# FlexibilityEvent Tests

This folder contains the tests for the `FlexibilityEvent` microservice. The code under test is the `FlexibilityEvent` model and the `FlexibilityEventResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/FlexibilityEventResourceTest.java`: unit tests for the `FlexibilityEventResource` and the `FlexibilityEvent` domain behaviour.
- `src/test/java/org/acme/FlexibilityEventResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `FlexibilityEventResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The unit tests also mock the `Emitter<String>` to verify messages published to the configured channel.

## What Is Covered

### Unit tests: `FlexibilityEventResourceTest`

These tests validate the behavior of the `FlexibilityEvent` model and `FlexibilityEventResource` in isolation:

- `getFlexibilityEvents_returnsList`: verifies that `get()` returns a list of `FlexibilityEvent` objects mapped from DB `Row` instances, including `id`, `assetId`, `prosumerId`, `eventType`, `soc_percent`, `recommendedAction`, `marketPrice`, `incentiveAmount`, `gridCellId`, and `timestamp`.
- `getFlexibilityEventById_returnsEntity`: verifies that `getSingle(id)` returns the matching entity when a row exists and that the `Response` status is `200`.
- `getFlexibilityEventById_returnsNotFound`: verifies that `getSingle(id)` returns `404` when the prepared query returns no rows.
- `getFlexibilityEventsByAssetId_returnsList`: verifies that `getByAsset(assetId)` returns only events with the requested `assetId` and that results are ordered by `timestamp` descending.
- `getFlexibilityEventsByEventType_returnsList`: verifies that `getByType(eventType)` returns only events with the requested `eventType` and that results are ordered by `timestamp` descending.
- `evaluateTelemetry_highSoC_createsArbitrageSellEvent`: verifies that posting telemetry with a high `State_of_Charge` (e.g., `95.0f`) creates an `ARBITRAGE_SELL` event, persists it (mocked insert returns a generated id), sets expected fields (`recommendedAction` = `DISCHARGE`, `marketPrice` = `150.0f`, `incentiveAmount` = `10.0f`), returns `200` and publishes a JSON message to the emitter containing `eventId`, `assetId`, `prosumerId`, `eventType`, `recommendedAction`, and `timestamp`.
- `evaluateTelemetry_lowSoC_createsBalancingUnavailableEvent`: verifies that posting telemetry with a low `State_of_Charge` (e.g., `15.0f`) creates a `BALANCING_UNAVAILABLE` event with `recommendedAction` = `UNAVAILABLE`, persists it (mocked id), and returns `200`.
- `evaluateTelemetry_normalSoC_returnsNoContent`: verifies that posting telemetry with a normal SoC (e.g., `50.0f`) returns `204 No Content` and does not persist an event.
- `evaluateTelemetry_nullSoC_returnsNoContent`: verifies that posting telemetry with `State_of_Charge = null` returns `204 No Content`.
- `evaluateTelemetry_publishesCorrectKafkaMessage`: verifies that when an event is created the `Emitter<String>` is invoked once and the emitted string is valid JSON containing the expected keys and values.

The unit tests also validate the exact SQL used by the model/resource methods:

- `SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent ORDER BY timestamp DESC`
- `SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE id = ?`
- `SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE assetId = ? ORDER BY timestamp DESC`
- `SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE eventType = ? ORDER BY timestamp DESC`
- `INSERT INTO FlexibilityEvent(assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp) VALUES (?,?,?,?,?,?,?,?,?)`

### Integration tests: `FlexibilityEventResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured:

- `getFlexibilityEvents_returnsList`: verifies `GET /FlexibilityEvent` returns a JSON array with all records.
- `getFlexibilityEventById_returnsEntity`: verifies `GET /FlexibilityEvent/{id}` returns one record when it exists.
- `getFlexibilityEventById_returnsNotFound`: verifies `GET /FlexibilityEvent/{id}` returns `404` when no record exists.
- `getFlexibilityEventsByAssetId_returnsList`: verifies `GET /FlexibilityEvent/asset/{assetId}` returns records with matching `assetId`.
- `getFlexibilityEventsByEventType_returnsList`: verifies `GET /FlexibilityEvent/type/{eventType}` returns records filtered by `eventType`.
- `evaluateTelemetry_highSoC_createsEvent`: verifies `POST /FlexibilityEvent/evaluate/{prosumerId}` returns `200` and the created event JSON when SoC is high (mocked DB insert provides generated id).
- `evaluateTelemetry_lowSoC_createsEvent`: verifies low SoC create returns `200` and the created event JSON.
- `evaluateTelemetry_normalSoC_returnsNoContent`: verifies `POST /FlexibilityEvent/evaluate/{prosumerId}` returns `204 No Content` when SoC is normal.

The integration tests mock the DB insert result to return a `LAST_INSERTED_ID` property used by the service to set the created entity `id`.

## How To Run

From the `FlexibilityEvent` module root:

To run only the unit test class:

```bash
mvn test
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.push=false
```
