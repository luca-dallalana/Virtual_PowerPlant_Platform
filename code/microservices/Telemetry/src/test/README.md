# Telemetry Tests

This folder contains the tests for the `Telemetry` microservice. The code under test is the `Telemetry` model and the `KafkaProvisioningResource` REST API.

## Test Types

The module has two test classes:

- `src/test/java/org/acme/KafkaProvisioningResourceTest.java`: unit tests for the `KafkaProvisioningResource` and the `Telemetry` domain behavior.
- `src/test/java/org/acme/KafkaProvisioningResourceIT.java`: Quarkus REST tests for the HTTP endpoints exposed by `KafkaProvisioningResource`.

Both test classes use Mockito to mock `MySQLPool` so the tests do not need a live database. The unit tests inject the mocked pool directly into the resource and disable schema initialization. The integration tests use `@QuarkusTest` with `@InjectMock` and RestAssured to exercise the endpoints through the Quarkus runtime.

## What Is Covered

### Unit tests: `KafkaProvisioningResourceTest`

These tests validate the behavior of the `Telemetry` model and `KafkaProvisioningResource` in isolation:

- `get_returnsList`: verifies that `get()` returns a list of `Telemetry` objects mapped from DB `Row` instances, including `id`, `timeStamp`, `asset_id`, `asset_type`, `grid_cell_id`, `State_of_Charge`, `Available_Energy`, `Current_Output`, `Max_Capacity`, `State_of_Health`, `Status`, `Current_Generation`, `Daily_Total`, `Grid_Voltage`, `Frequency`, `Plug_Status`, `Charging_Rate`, `Session_Energy`, and `EV_SoC`.
- `getSingle_returnsEntity`: verifies that `getSingle(id)` returns the matching entity when a row exists and that the `Response` status is `200`.
- `getSingle_returnsNotFound`: verifies that `getSingle(id)` returns `404` when the prepared query returns no rows.

The unit tests also validate the exact SQL used by the model/resource methods:

- `SELECT *  FROM Telemetry ORDER BY id ASC`
- `SELECT * FROM Telemetry WHERE id = ?`

### Integration tests: `KafkaProvisioningResourceIT`

These tests exercise the REST resource through HTTP using `@QuarkusTest` and RestAssured:

- `getTelemetry_returnsList`: verifies `GET /Telemetry` returns a JSON array with all records.
- `getTelemetryById_returnsEntity`: verifies `GET /Telemetry/{id}` returns one record when it exists.
- `getTelemetryById_returnsNotFound`: verifies `GET /Telemetry/{id}` returns `404` when no record exists.
- `postConsumeEndpoint_returnsSuccessMessage`: verifies `POST /Telemetry/Consume` returns `200` and the `New worker started` response when a valid topic payload is provided.
- `postConsumeEndpoint_withMalformedJson_returns4xx`: verifies malformed JSON posted to `/Telemetry/Consume` fails with a client or server error response.

The integration tests reuse the same mocked database result helpers to verify the response mapping from MySQL rows to JSON.

## How To Run

From the `Telemetry` module root:

To run only the unit test class:

```bash
mvn test
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.push=false
```