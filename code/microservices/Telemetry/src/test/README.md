# Telemetry Tests

This folder contains the tests for the `Telemetry` microservice. The code under test is the `Telemetry` model, the `KafkaProvisioningResource` REST API, and the `DynamicTopicConsumer` Kafka consumer thread.

## Test Types

The module has three test classes:

- `src/test/java/org/acme/KafkaProvisioningResourceTest.java`: unit tests for the `KafkaProvisioningResource` and the `Telemetry` domain behavior.
- `src/test/java/org/acme/KafkaProvisioningResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `KafkaProvisioningResource`.
- `src/test/java/org/acme/DynamicTopicConsumerSimulatorTest.java`: integration tests for the `DynamicTopicConsumer` Kafka consumer thread using an embedded Kafka broker and the VPPaaS simulator message format.

The unit and IT tests use Mockito to mock `MySQLPool` so they do not need a live database. The simulator integration tests use Testcontainers (`confluentinc/cp-kafka:7.5.0`) to run a real Kafka broker in-process, produce messages in the exact JSON format emitted by the VPPaaS Event Producer simulator, and verify that `DynamicTopicConsumer` parses and persists them correctly.

## What Is Covered

### Unit tests: `KafkaProvisioningResourceTest`

| Test | What it verifies |
|---|---|
| `get_returnsList` | `get(null, null)` returns all rows mapped to `Telemetry` objects via `findAll` |
| `getSingle_returnsEntity` | `getSingle(id)` returns `200` and the matching entity when a row exists |
| `getSingle_returnsNotFound` | `getSingle(id)` returns `404` when no row exists |
| `getLatestByAssetId_returnsEntity` | `getLatestByAssetId(assetId)` returns `200` and the most recent reading |
| `getLatestByAssetId_returnsNotFound` | `getLatestByAssetId(assetId)` returns `404` when no row exists |
| `get_withTimeWindow_returnsFiltered` | `get(from, to)` routes to `findByTimeWindow` and returns rows within the time range |
| `getLatestByAssetType_returnsList` | `getLatestByAssetType(assetType, minutes)` returns the latest reading per asset using the JOIN subquery |
| `getWindowByAssetType_returnsList` | `getWindowByAssetType(assetType, minutes)` returns all readings within the rolling time window |
| `getLatestByAssetIds_returnsList` | `getLatestByAssetIds(assetIds, null)` returns the latest reading per asset ID using a dynamic IN query |
| `getLatestByAssetIds_emptyInput_returnsEmpty` | `getLatestByAssetIds([], null)` short-circuits without hitting the DB and returns an empty list |

### Integration tests: `KafkaProvisioningResourceIT`

| Test | Endpoint | What it verifies |
|---|---|---|
| `getTelemetry_returnsList` | `GET /Telemetry` | Returns a JSON array with all records |
| `getTelemetryById_returnsEntity` | `GET /Telemetry/{id}` | Returns the record when it exists |
| `getTelemetryById_returnsNotFound` | `GET /Telemetry/{id}` | Returns `404` when not found |
| `getTelemetry_withTimeWindow_returnsFiltered` | `GET /Telemetry?from=...&to=...` | Returns records within the time window when both query params are present |
| `getLatestByAssetId_returnsEntity` | `GET /Telemetry/latest/{assetId}` | Returns the most recent reading for the asset |
| `getLatestByAssetId_returnsNotFound` | `GET /Telemetry/latest/{assetId}` | Returns `404` when no reading exists for the asset |
| `getLatestByAssetType_returnsList` | `GET /Telemetry/latest/{assetType}/{minutes}` | Returns the latest reading per asset of the given type within the time window |
| `getWindowByAssetType_returnsList` | `GET /Telemetry/window/{assetType}/{minutes}` | Returns all readings of the given type within the rolling window |
| `getLatestByAssetIds_returnsList` | `POST /Telemetry/latest/bulk` | Returns the latest reading for each asset ID in the request body |
| `getLatestByAssetIds_emptyInput_returnsEmpty` | `POST /Telemetry/latest/bulk` | Returns an empty array when the input list is empty |
| `postConsumeEndpoint_returnsSuccessMessage` | `POST /Telemetry/Consume` | Returns `200` and `"New worker started"` when a valid topic payload is provided |
| `postConsumeEndpoint_withMalformedJson_returns4xx` | `POST /Telemetry/Consume` | Returns `4xx` or `5xx` when the request body is malformed JSON |

### Simulator integration tests: `DynamicTopicConsumerSimulatorTest`

These tests validate the full Kafka consumption pipeline using a real embedded Kafka broker (Testcontainers). Each test creates a unique UUID-suffixed topic, starts the consumer thread as a daemon, produces a simulator message, and uses `ArgumentCaptor` to verify the `INSERT INTO Telemetry` tuple values.

| Test | Asset type | Key assertions |
|---|---|---|
| `batteryMessage_parsedAndPersistedToDatabase` | `BATTERY` | `State_of_Charge = 85.5`, `Status = "ONLINE"`, SOLAR and EV_CHARGER columns are `null` |
| `solarMessage_parsedAndPersistedToDatabase` | `SOLAR` | `Current_Generation = 5.2`, BATTERY and EV_CHARGER columns are `null` |
| `evChargerMessage_parsedAndPersistedToDatabase` | `EV_CHARGER` | `Plug_Status = "CHARGING"`, `Charging_Rate = 11.5`, BATTERY and SOLAR columns are `null` |

Each test creates a unique topic (UUID-suffixed) to prevent cross-test consumer group offset interference. The consumer thread is interrupted in `@AfterEach` cleanup.

## SQL Verified

All stubs match the production SQL character-for-character. A mismatch causes Mockito to return `null`, which propagates as a `500` at runtime.

```sql
SELECT *  FROM Telemetry ORDER BY id ASC
SELECT * FROM Telemetry WHERE id = ?
SELECT * FROM Telemetry WHERE timeStamp >= ? AND timeStamp <= ? ORDER BY timeStamp ASC
SELECT * FROM Telemetry WHERE asset_id = ? ORDER BY timeStamp DESC LIMIT 1
SELECT * FROM Telemetry WHERE asset_type = ? AND timeStamp >= ? ORDER BY asset_id ASC, timeStamp ASC
SELECT t.* FROM Telemetry t INNER JOIN (SELECT asset_id, MAX(timeStamp) as maxTs FROM Telemetry WHERE asset_type = ? AND timeStamp >= ? GROUP BY asset_id) latest ON t.asset_id = latest.asset_id AND t.timeStamp = latest.maxTs
SELECT t.* FROM Telemetry t INNER JOIN (SELECT asset_id, MAX(timeStamp) as maxTs FROM Telemetry WHERE asset_id IN (?, ?) GROUP BY asset_id) latest ON t.asset_id = latest.asset_id AND t.timeStamp = latest.maxTs
```

Note: `SELECT *  FROM Telemetry ORDER BY id ASC` has a double space after `*` — this must match exactly. The `IN (?, ?)` placeholder count in the last SELECT is dynamic and matches the number of asset IDs in the request body; the unit test uses a two-element list to verify the query shape.

## How To Run

From the `Telemetry` module root:

```bash
# Unit tests only
./mvnw clean test -Dtest=KafkaProvisioningResourceTest

# Integration tests
./mvnw clean test -Dtest=KafkaProvisioningResourceIT

# Both in one command
./mvnw clean test -Dtest="KafkaProvisioningResourceTest,KafkaProvisioningResourceIT"

# Simulator integration tests (requires Docker for the Kafka container)
./mvnw clean test -Dtest=DynamicTopicConsumerSimulatorTest

# All tests
./mvnw clean test
```
