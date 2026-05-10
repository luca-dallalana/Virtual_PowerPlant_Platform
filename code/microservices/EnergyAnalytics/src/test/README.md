# EnergyAnalytics Tests

This folder contains the tests for the `EnergyAnalytics` microservice. The code under test is the `EnergyAnalyticsResource` REST API and its interaction contract with the `AnalyticsCalculationService` and entity query methods.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/EnergyAnalyticsResourceTest.java`: unit tests for `EnergyAnalyticsResource` behavior with mocked `MySQLPool` and mocked `AnalyticsCalculationService`.
- `src/test/java/org/acme/EnergyAnalyticsResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `EnergyAnalyticsResource`.
- `src/test/java/org/acme/EnergyAnalyticsKafkaTest.java`: Kafka logic tests that use the SmallRye in-memory connector to validate emitted messages and Kafka keys.

Both test classes use Mockito to mock `MySQLPool` so tests do not need a live database. For `POST /EnergyAnalytics/evaluate`, both classes also mock `AnalyticsCalculationService` to isolate API behavior and response mapping.

The Kafka test uses the real `AnalyticsCalculationService` with mocked persistence and verifies the outgoing messages produced on the `energy-discharged-zone`, `energy-generated-prosumer`, `energy-consumed-prosumer`, and `average-soc` channels.

## What Is Covered

### Unit tests: `EnergyAnalyticsResourceTest`

These tests validate the behavior of `EnergyAnalyticsResource` in isolation:

- `getDischargedByZone_returnsList`: verifies `getDischargedByZone()` returns mapped `EnergyDischargedByZone` objects from mocked DB `Row` instances, including `id`, `gridCellId`, `totalEnergyDischargedKw`, `batteryCount`, `timestamp`, and `aggregationPeriod`.
- `getDischargedByGridCell_returnsFiltered`: verifies `getDischargedByGridCell(gridCellId)` returns only records for the requested grid cell.
- `getGeneratedByProsumer_returnsList`: verifies `getGeneratedByProsumer()` returns mapped `GeneratedEnergyByProsumer` records with expected fields.
- `getGeneratedByProsumerId_returnsFiltered`: verifies `getGeneratedByProsumerId(prosumerId)` filters by prosumer id.
- `getConsumedByProsumer_returnsList`: verifies `getConsumedByProsumer()` returns mapped `ConsumedEnergyByProsumer` records with expected fields.
- `getConsumedByProsumerId_returnsFiltered`: verifies `getConsumedByProsumerId(prosumerId)` filters by prosumer id.
- `getAverageSoC_returnsList`: verifies `getAverageSoC()` returns mapped `AverageSoC` records with expected fields.
- `evaluate_withAllAssetTypes_createsRecords`: verifies `evaluate(request)` returns `200` with `status = SUCCESS` and `recordsProcessed = 3` when telemetry includes BATTERY, SOLAR, and EV_CHARGER with linked assets.
- `evaluate_withPartialData_createsRecords`: verifies `evaluate(request)` returns `200` and expected service result for partial telemetry/link payloads.
- `evaluate_withEmptyData_returnsZeroRecords`: verifies `evaluate(request)` returns `200` and `recordsProcessed = 0` for empty telemetry/link lists.
- `evaluate_withNullFieldsInTelemetry_handlesGracefully`: verifies `evaluate(request)` returns `200` and delegates once to `AnalyticsCalculationService` when telemetry contains null measurement fields.
- `evaluate_withUnlinkedAssets_filtersCorrectly`: verifies `evaluate(request)` returns `200` and delegates once when telemetry contains asset ids that are not linked in provided `assetLinks`.
- `kafka_publish_four_metrics_withKeys`: verifies the aggregation service publishes four JSON payloads through the in-memory connector and attaches the expected Kafka keys for each outgoing channel.

The unit tests also validate the exact SQL used by the entity finder methods called by the resource:

- `SELECT id, gridCellId, totalEnergyDischargedKw, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone ORDER BY timestamp DESC`
- `SELECT id, gridCellId, totalEnergyDischargedKw, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone WHERE gridCellId = ? ORDER BY timestamp DESC`
- `SELECT id, prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer ORDER BY timestamp DESC`
- `SELECT id, prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC`
- `SELECT id, prosumerId, totalEnergyConsumedKw, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer ORDER BY timestamp DESC`
- `SELECT id, prosumerId, totalEnergyConsumedKw, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC`
- `SELECT id, averageSocPercent, batteryCount, timestamp, aggregationPeriod FROM AverageSoC ORDER BY timestamp DESC`

### Integration tests: `EnergyAnalyticsResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured:

- `getDischargedByZone_returnsList`: verifies `GET /EnergyAnalytics/discharged-by-zone` returns a JSON array with all records.
- `getDischargedByGridCell_returnsFiltered`: verifies `GET /EnergyAnalytics/discharged-by-zone/{gridCellId}` returns records with matching `gridCellId`.
- `getGeneratedByProsumer_returnsList`: verifies `GET /EnergyAnalytics/generated-by-prosumer` returns all generated-energy records.
- `getGeneratedByProsumerId_returnsFiltered`: verifies `GET /EnergyAnalytics/generated-by-prosumer/{prosumerId}` returns records filtered by `prosumerId`.
- `getConsumedByProsumer_returnsList`: verifies `GET /EnergyAnalytics/consumed-by-prosumer` returns all consumed-energy records.
- `getConsumedByProsumerId_returnsFiltered`: verifies `GET /EnergyAnalytics/consumed-by-prosumer/{prosumerId}` returns records filtered by `prosumerId`.
- `getAverageSoC_returnsList`: verifies `GET /EnergyAnalytics/average-soc` returns average SoC records.
- `evaluate_withAllAssetTypes_returnsSuccess`: verifies `POST /EnergyAnalytics/evaluate` returns `200` with `status = SUCCESS` and `recordsProcessed = 3`.
- `evaluate_withPartialData_returnsSuccess`: verifies `POST /EnergyAnalytics/evaluate` returns `200` with expected success payload for partial data.
- `evaluate_withEmptyData_returnsZeroRecords`: verifies `POST /EnergyAnalytics/evaluate` returns `200` with `recordsProcessed = 0` for empty lists.

The integration tests mock query and prepared-query responses and validate endpoint behavior without a real database.

## How To Run

From the `EnergyAnalytics` module root:

To run only the unit/integration tests in the test phase:

```bash
mvn test
```

Run only the Kafka logic test:
```bash
mvn test -Dtest=EnergyAnalyticsKafkaTest
```

Run full verification lifecycle:
```sh
mvn clean verify
```
