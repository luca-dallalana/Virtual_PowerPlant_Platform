# EnergyAnalytics Tests

This folder contains the tests for the `EnergyAnalytics` microservice. The code under test is the `EnergyAnalyticsResource` REST API and the `AnalyticsCalculationService` business logic.

## Test Types

The module has three test classes:

- `src/test/java/org/acme/EnergyAnalyticsResourceTest.java`: unit tests for `EnergyAnalyticsResource` with mocked `MySQLPool` and mocked `AnalyticsCalculationService`.
- `src/test/java/org/acme/EnergyAnalyticsResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `EnergyAnalyticsResource`.
- `src/test/java/org/acme/EnergyAnalyticsKafkaTest.java`: unit tests for `AnalyticsCalculationService` covering both persistence and computation methods directly, with mocked `MySQLPool` injected via reflection.

The resource test classes mock `MySQLPool` (for read endpoints) and `AnalyticsCalculationService` (for compute/persist endpoints) so the tests do not need a live database. The unit test injects mocks via reflection; the IT test uses `@InjectMock`.

## What Is Covered

### Unit tests: `EnergyAnalyticsResourceTest`

#### Read endpoint tests

| Test | What it verifies |
|---|---|
| `getDischargedByZone_returnsList` | `getDischargedByZone()` returns mapped `EnergyDischargedByZone` records including `id`, `gridCellId`, `totalEnergyDischargedKwh`, `batteryCount`, `timestamp`, and `aggregationPeriod` |
| `getDischargedByGridCell_returnsFiltered` | `getDischargedByGridCell(gridCellId)` returns only records for the requested grid cell |
| `getGeneratedByProsumer_returnsList` | `getGeneratedByProsumer()` returns mapped `GeneratedEnergyByProsumer` records with all fields |
| `getGeneratedByProsumerId_returnsFiltered` | `getGeneratedByProsumerId(prosumerId)` filters results by prosumer id |
| `getConsumedByProsumer_returnsList` | `getConsumedByProsumer()` returns mapped `ConsumedEnergyByProsumer` records with all fields |
| `getConsumedByProsumerId_returnsFiltered` | `getConsumedByProsumerId(prosumerId)` filters results by prosumer id |
| `getAverageSoC_returnsList` | `getAverageSoC()` returns mapped `AverageSoC` records with `id`, `averageSocPercent`, `batteryCount`, `timestamp`, and `aggregationPeriod` |

#### Compute endpoint tests

| Test | What it verifies |
|---|---|
| `computeGeneratedByProsumer_returnsList` | `computeGeneratedByProsumer(request)` delegates to the service and returns `200` with the result list |
| `computeConsumedByProsumer_returnsList` | `computeConsumedByProsumer(request)` delegates to the service and returns `200` with the result list |
| `computeDischargedByZone_returnsList` | `computeDischargedByZone(request)` delegates to the service and returns `200` with the result list |
| `computeAverageSoC_returnsResult` | `computeAverageSoC(request)` delegates to the service and returns `200` with the result |

#### Persist endpoint tests

| Test | What it verifies |
|---|---|
| `persistConsumed_returnsSuccess` | `persistConsumed(request)` delegates to the service and returns `200` with the saved records including populated `id` |
| `persistGenerated_returnsSuccess` | `persistGenerated(request)` delegates to the service and returns `200` with the saved records including populated `id` |
| `persistDischarged_returnsSuccess` | `persistDischarged(request)` delegates to the service and returns `200` with the saved records including populated `id` |
| `persistAverage_returnsSuccess` | `persistAverage(request)` delegates to the service and returns `200` with the saved `AverageSoC` including populated `id` |

### Service unit tests: `EnergyAnalyticsKafkaTest`

These tests call `AnalyticsCalculationService` methods directly with a mocked `MySQLPool`, verifying both persistence and computation logic.

#### Persistence tests

| Test | What it verifies |
|---|---|
| `persistConsumed_savesAllRecords` | `persistConsumed(list)` inserts each record and returns the list with `id` set from `LAST_INSERTED_ID` |
| `persistConsumed_emptyList_returnsEmpty` | `persistConsumed([])` short-circuits and returns an empty list |
| `persistGenerated_savesAllRecords` | `persistGenerated(list)` inserts each record and returns the list with `id` populated |
| `persistGenerated_emptyList_returnsEmpty` | `persistGenerated([])` short-circuits and returns an empty list |
| `persistDischarged_savesAllRecords` | `persistDischarged(list)` inserts each record and returns the list with `id` populated |
| `persistDischarged_emptyList_returnsEmpty` | `persistDischarged([])` short-circuits and returns an empty list |
| `persistAverageSoC_savesRecord` | `persistAverageSoC(avgSoC)` inserts the record and returns it with `id` populated |
| `persistAverageSoC_nullInput_returnsNull` | `persistAverageSoC(null)` returns `null` without hitting the DB |

#### Compute tests

| Test | What it verifies |
|---|---|
| `computeGeneratedByProsumer_singleSolarAsset_returnsCorrectEnergy` | Single SOLAR asset at 100 kW produces 50.0 kWh (100 kW × 0.5 h window) |
| `computeGeneratedByProsumer_twoAssetsOneProsumer_sumsEnergy` | Two SOLAR assets (100 kW + 60 kW) for the same prosumer sum to 80.0 kWh |
| `computeGeneratedByProsumer_nonSolarFiltered` | Non-SOLAR telemetry (e.g. BATTERY) is excluded from generated-energy results |
| `computeGeneratedByProsumer_emptyTelemetry_returnsEmpty` | Empty telemetry list returns an empty result |
| `computeConsumedByProsumer_singleEvCharger_returnsCorrectEnergy` | Single EV_CHARGER at 25 kW produces 12.5 kWh (25 kW × 0.5 h window) |
| `computeConsumedByProsumer_nonEvChargerFiltered` | Non-EV_CHARGER telemetry is excluded from consumed-energy results |
| `computeDischargedByZone_singleBattery_returnsCorrectEnergy` | Single BATTERY at 50 kW positive output produces 25.0 kWh for its zone |
| `computeDischargedByZone_negativeOutput_treatedAsZero` | Negative battery output is clamped to 0.0 kWh |
| `computeDischargedByZone_noMatchingAssets_emitsZeroRecord` | A zone with no matching telemetry still emits a record with `totalEnergyDischargedKwh = 0.0` and `batteryCount = 0` |
| `computeDischargedByZone_unknownGridCell_excluded` | Battery telemetry referencing an unknown `gridCellId` is not counted for any zone |
| `computeAverageSoC_multipleReadingsSameAsset_averagesPerAsset` | Multiple readings for the same asset are averaged per-asset before aggregating (e.g. SoC 80 + 60 → asset avg 70, fleet avg 70) |
| `computeAverageSoC_twoAssets_averagesAcrossAssets` | Two distinct assets (80 and 60 SoC) produce a fleet average of 70.0 with `batteryCount = 2` |
| `computeAverageSoC_noBattery_returnsZero` | Non-BATTERY telemetry results in `averageSocPercent = 0.0` and `batteryCount = 0` |

### Integration tests: `EnergyAnalyticsResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured. `MySQLPool` and `AnalyticsCalculationService` are replaced with Mockito mocks via `@InjectMock`.

#### Read endpoints

| Test | Endpoint | What it verifies |
|---|---|---|
| `getDischargedByZone_returnsList` | `GET /EnergyAnalytics/discharged-by-zone` | Returns a JSON array with all discharged-energy records |
| `getDischargedByGridCell_returnsFiltered` | `GET /EnergyAnalytics/discharged-by-zone/{gridCellId}` | Returns records filtered by `gridCellId` |
| `getGeneratedByProsumer_returnsList` | `GET /EnergyAnalytics/generated-by-prosumer` | Returns all generated-energy records |
| `getGeneratedByProsumerId_returnsFiltered` | `GET /EnergyAnalytics/generated-by-prosumer/{prosumerId}` | Returns records filtered by `prosumerId` |
| `getConsumedByProsumer_returnsList` | `GET /EnergyAnalytics/consumed-by-prosumer` | Returns all consumed-energy records |
| `getConsumedByProsumerId_returnsFiltered` | `GET /EnergyAnalytics/consumed-by-prosumer/{prosumerId}` | Returns records filtered by `prosumerId` |
| `getAverageSoC_returnsList` | `GET /EnergyAnalytics/average-soc` | Returns all average SoC records |

#### Compute endpoints

| Test | Endpoint | What it verifies |
|---|---|---|
| `computeGeneratedByProsumer_returnsResult` | `POST /EnergyAnalytics/compute/generated-by-prosumer` | Returns `200` with the list of generated-energy results from the service |
| `computeConsumedByProsumer_returnsResult` | `POST /EnergyAnalytics/compute/consumed-by-prosumer` | Returns `200` with the list of consumed-energy results from the service |
| `computeDischargedByZone_returnsResult` | `POST /EnergyAnalytics/compute/discharged-by-zone` | Returns `200` with the list of discharged-energy results from the service |
| `computeAverageSoC_returnsResult` | `POST /EnergyAnalytics/compute/average-soc` | Returns `200` with the `AverageSoC` result from the service |

#### Persist endpoints

| Test | Endpoint | What it verifies |
|---|---|---|
| `persistConsumed_returnsSuccess` | `POST /EnergyAnalytics/persist/consume` | Returns `200` with the persisted records including `id` |
| `persistGenerated_returnsSuccess` | `POST /EnergyAnalytics/persist/generate` | Returns `200` with the persisted records including `id` |
| `persistDischarged_returnsSuccess` | `POST /EnergyAnalytics/persist/discharge` | Returns `200` with the persisted records including `id` and `gridCellId` |
| `persistAverage_returnsSuccess` | `POST /EnergyAnalytics/persist/average` | Returns `200` with the persisted `AverageSoC` including `id` and `averageSocPercent` |

## SQL Verified

All stubs match the production SQL character-for-character. A mismatch causes Mockito to return `null`, which propagates as a `500` at runtime.

**Read queries:**
```sql
SELECT id, gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone ORDER BY timestamp DESC
SELECT id, gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone WHERE gridCellId = ? ORDER BY timestamp DESC
SELECT id, prosumerId, totalEnergyGeneratedKwh, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer ORDER BY timestamp DESC
SELECT id, prosumerId, totalEnergyGeneratedKwh, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC
SELECT id, prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer ORDER BY timestamp DESC
SELECT id, prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC
SELECT id, averageSocPercent, batteryCount, timestamp, aggregationPeriod FROM AverageSoC ORDER BY timestamp DESC
```

**Insert queries (service persist methods):**
```sql
INSERT INTO ConsumedEnergyByProsumer(prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)
INSERT INTO GeneratedEnergyByProsumer(prosumerId, totalEnergyGeneratedKwh, solarAssetCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)
INSERT INTO EnergyDischargedByZone(gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)
INSERT INTO AverageSoC(averageSocPercent, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?)
```

## How To Run

From the `EnergyAnalytics` module root:

```bash
# Unit tests only (ResourceTest + KafkaTest)
./mvnw clean test

# Integration tests (excluded by Surefire by default, must be named explicitly)
./mvnw clean test -Dtest=EnergyAnalyticsResourceIT

# All three test classes in one command
./mvnw clean test -Dtest="EnergyAnalyticsResourceTest,EnergyAnalyticsKafkaTest,EnergyAnalyticsResourceIT"
```
