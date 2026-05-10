# GridBalancingRecommendation Microservice

## 1. Overview

The GridBalancingRecommendation microservice analyzes aggregated data across different Grid Zones to detect imbalances and recommend load-shifting opportunities.
- Evaluates grid zone conditions by analyzing telemetry from all connected assets (solar generators, batteries, EV chargers).
- Calculates net load (demand minus supply) for each grid zone against a configurable safety threshold.
- Identifies zones experiencing overload (exceeding threshold) and scans neighboring zones for available surplus capacity.
- Generates balancing recommendations that specify source zone, target zone, transferable power capacity, and recommended actions.
- Persists generated recommendations in MySQL using Quarkus reactive client and Mutiny.
- Publishes balancing recommendations to "GridBalancingRecommendation" Kafka topic for downstream consumption (e.g., Utility Operator Manager).

This service represents the critical grid stabilization and load-balancing capability of the VPPaaS. For example, if Zone A is experiencing an overload (net load exceeds 90% of max capacity) and Zone B has surplus capacity, the system emits a recommendation to shift load from Zone A to Zone B.

## 2. API Endpoints

Base path: `/GridBalancingRecommendation`

GridBalancingRecommendation endpoints:
- `GET /GridBalancingRecommendation`
- `GET /GridBalancingRecommendation/{id}`
- `GET /GridBalancingRecommendation/source/{gridCellId}`
- `POST /GridBalancingRecommendation/evaluate`
- `POST /GridBalancingRecommendation`
- `PUT /GridBalancingRecommendation/{id}`
- `DELETE /GridBalancingRecommendation/{id}`

The `POST /GridBalancingRecommendation/evaluate` endpoint accepts a request containing telemetry data (JSON array of telemetry records) and grid cell definitions (JSON array of grid zones with max capacity). It processes the data and returns:
- `200` with a list of created `BalancingRecommendation` objects when recommendations are generated.
- `200` with an empty list when telemetry does not trigger any recommendations.

Swagger UI available at:
- `http://localhost:8085/q/swagger-ui`

## 3. Source Code Organization

Runtime code:
- `src/main/java/org/acme/GridBalancingRecommendationResource.java`: REST resource, orchestration logic, endpoint handlers.
- `src/main/java/org/acme/services/GridBalancingRecommendationService.java`: Core business logic for analyzing zones, calculating net loads, identifying target zones, and emitting recommendations.
- `src/main/java/org/acme/entities/BalancingRecommendation.java`: Domain model and database operations.
- `src/main/java/org/acme/dto/GridBalancingRequest.java`: Request DTO containing telemetry and grid cell data.
- `src/main/java/org/acme/dto/TelemetryDTO.java`: Telemetry DTO with asset data and grid cell references.
- `src/main/java/org/acme/dto/GridCellDTO.java`: Grid cell DTO with capacity and geographic boundaries.

Configuration:
- `src/main/resources/application.properties`

Local container execution:
- `docker-compose.yml`

## 4. Tests Documentation

### 4.1 Test Suites

Unit-style tests (direct method calls with mocked DB client and mocked emitter):
- `src/test/java/org/acme/GridBalancingRecommendationResourceTest.java`

HTTP integration-style tests (Quarkus runtime with RestAssured and mocked DB client):
- `src/test/java/org/acme/GridBalancingRecommendationResourceIT.java`

Kafka logic tests with the SmallRye in-memory connector:
- `src/test/java/org/acme/GridBalancingRecommendationKafkaTest.java`

Test configuration:
- `src/test/resources/application.properties`

### 4.2 Covered Functional Scenarios

GridBalancingRecommendation scenarios:
- Get all balancing recommendations.
- Get recommendation by id (found and not found).
- Get recommendations by source grid cell id (non-empty and empty responses).
- Evaluate telemetry to create recommendations:
  - Overloaded zone (net load > threshold) with available target zone -> `RECOMMENDED` with `transferableKw` and target details.
  - Overloaded zone with no available target zone -> `NO_TARGET` with explanation.
  - Normal zones (net load <= threshold) -> no recommendation generated.
- Create, update, and delete balancing recommendations.
- Asset type handling:
  - EV_CHARGER increases demand (charging rate).
  - SOLAR increases supply (current generation).
  - BATTERY increases supply (positive output) or demand (negative output/charging).
- Publish the Kafka message when a `RECOMMENDED` record is created.
- Verify the emitted Kafka key matches the source grid cell id.

### 4.3 What Is Validated

- REST behavior (status codes and response structure).
- Domain mapping from DB result to API responses.
- Correct handling of success, not-found, and no-content outcomes.
- Recommendation persistence and returned inserted id behavior.
- Kafka message publishing when a RECOMMENDED recommendation is created.
- Accurate net load calculation (demand - supply).
- Correct threshold enforcement and zone comparison logic.
- Target zone selection based on maximum headroom strategy.
- Proper handling of null/missing telemetry values and optional fields.

### 4.4 How to Run Tests

From this folder (`code/microservices/GridBalancingRecommendation`):

Run tests:
```sh
./mvnw clean test
```

Run only the Kafka logic test:
```sh
./mvnw test -Dtest=GridBalancingRecommendationKafkaTest
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.push=false
```

## 5. Installation and Execution Procedures

### 5.1 Prerequisites

- Java 17
- Docker and Docker Compose (for container run)
- Maven wrapper support

### 5.2 Run in Dev Mode (Quarkus) — For Development

Fastest iteration with live reload. Requires MySQL and Kafka running in a separate terminal:

```sh
docker compose up
```

Then in another terminal:

```sh
./mvnw compile quarkus:dev
```

Changes are reflected immediately. Access at `http://localhost:8085/q/swagger-ui`

### 5.3 Run with Docker Compose — For Testing

All-in-one containerized setup. No live reload; rebuild required for code changes:

```sh
docker compose up --build
```

Services:
- **GridBalancingRecommendation API**: `http://localhost:8085`
- **Swagger UI**: `http://localhost:8085/q/swagger-ui`
- **MySQL**: `localhost:3310`
- **Kafka**: `localhost:9092` (if included in the compose file)

## 6. Terraform Scripts and Deployment

GridBalancingRecommendation Terraform module:
- `../../Quarkus-Terraform/gridbalancingrecommendation/EC2InstallQuarkus.tf`

GridBalancingRecommendation startup bootstrap script:
- `../../Quarkus-Terraform/gridbalancingrecommendation/quarkus.sh`

Shared deployment automation script:
- `../../DeploymentAutomation-macOS.sh`

Shared RDS Terraform module:
- `../../RDS-Terraform/RDSCreation-S3statefile.tf`

### 6.1 GridBalancingRecommendation Terraform Module Summary

The module:
- Provisions an EC2 instance for GridBalancingRecommendation deployment.
- Provisions a security group.
- Executes a startup script that installs Docker and runs the GridBalancingRecommendation container.

## 7. Parametrization

### 7.1 Application Parameters

Defined in `src/main/resources/application.properties` (examples used by this service):

- `quarkus.http.port=8085`
- `quarkus.datasource.db-kind=mysql`
- `quarkus.datasource.username=teste`
- `quarkus.datasource.password=testeteste`
- `quarkus.datasource.reactive.url=mysql://localhost:3306/gridbalancingrecommendation`
- `quarkus.swagger-ui.path=swagger-ui`
- `quarkus.swagger-ui.always-include=true`
- `quarkus.container-image.build=true`
- `quarkus.container-image.push=true`
- `myapp.schema.create=true` (controls automatic DB initialization on startup)
- `gridbalancing.threshold.percent=0.9` (net load threshold as fraction of max capacity; 0.9 = 90%)
- `kafka.bootstrap.servers` (Kafka bootstrap servers used by reactive messaging)
- `mp.messaging.outgoing.grid-balancing-recommendation.connector=smallrye-kafka` (Kafka connector configuration)
- `mp.messaging.outgoing.grid-balancing-recommendation.topic=GridBalancingRecommendation` (Kafka topic for publishing recommendations)
- `mp.messaging.outgoing.grid-balancing-recommendation.value.serializer=org.apache.kafka.common.serialization.StringSerializer` (Kafka serializer)

### 7.2 Test Parameters

Defined in `src/test/resources/application.properties`:

- `myapp.schema.create=false`
- `quarkus.kafka.devservices.enabled=false`
- `gridbalancing.threshold.percent=0.9`

### 7.3 Docker Compose Parameters

Docker compose should expose environment variables for MySQL and the service. Example variables used by this service:

MySQL:
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`

GridBalancingRecommendation service:
- `QUARKUS_DATASOURCE_REACTIVE_URL`
- `QUARKUS_DATASOURCE_USERNAME`
- `QUARKUS_DATASOURCE_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`

### 7.4 Terraform Parameters

Defined in `../../Quarkus-Terraform/gridbalancingrecommendation/EC2InstallQuarkus.tf` (example):

- `variable "security_group_name"` with a default like `terraform-Quarkus-instance-gridbalancingrecommendation2026`

## 8. Implementation Notes

- The service analyzes grid zones based on asset telemetry and capacity constraints.
- Asset types are categorized as:
  - **EV_CHARGER**: Adds charging rate to zone demand.
  - **SOLAR**: Adds current generation to zone supply.
  - **BATTERY**: Adds output to supply (if positive) or charging current to demand (if negative).
- **Net Load Calculation**: For each grid zone, net load = demand - supply (clamped to minimum of 0).
- **Threshold Enforcement**: A zone is considered overloaded when net load exceeds `threshold_percent * max_capacity`.
- **Target Zone Selection**: When a source zone is overloaded, the service selects the target zone with the **maximum available headroom** (surplus capacity above its threshold).
- **Recommendation Status**:
  - `RECOMMENDED`: Generated when an overloaded source zone finds a suitable target zone with surplus capacity.
  - `NO_TARGET`: Generated when an overloaded source zone exists but no neighboring zone has surplus capacity.
  - `MANUAL`: Manually created recommendations not derived from automatic evaluation.
- **Transferable Capacity**: Calculated as the minimum of the source zone's overload amount and the target zone's available headroom.
