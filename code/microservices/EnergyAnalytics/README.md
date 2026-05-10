# EnergyAnalytics Microservice

## 1. Overview

The EnergyAnalytics microservice aggregates telemetry into operational energy metrics for the VPPaaS.
- Calculates discharged energy by grid zone from battery telemetry.
- Calculates generated energy by prosumer from solar telemetry.
- Calculates consumed energy by prosumer from EV charger telemetry.
- Calculates average state of charge across battery assets.
- Persists aggregated metrics in MySQL using Quarkus reactive client and Mutiny.
- Publishes the aggregated metrics to Kafka for downstream consumers.

This service provides the analytical layer used by dashboards and orchestration flows to understand energy production, consumption, and battery state across the platform.

## 2. API Endpoints

Base path: `/EnergyAnalytics`

EnergyAnalytics endpoints:
- `POST /EnergyAnalytics/evaluate` - Aggregate telemetry and asset-link data, persist the calculated metrics, and publish Kafka events.
- `GET /EnergyAnalytics/discharged-by-zone` - List all discharged-energy records by zone.
- `GET /EnergyAnalytics/discharged-by-zone/{gridCellId}` - List discharged-energy records for a specific grid cell.
- `GET /EnergyAnalytics/generated-by-prosumer` - List all generated-energy records by prosumer.
- `GET /EnergyAnalytics/generated-by-prosumer/{prosumerId}` - List generated-energy records for a specific prosumer.
- `GET /EnergyAnalytics/consumed-by-prosumer` - List all consumed-energy records by prosumer.
- `GET /EnergyAnalytics/consumed-by-prosumer/{prosumerId}` - List consumed-energy records for a specific prosumer.
- `GET /EnergyAnalytics/average-soc` - List all average SoC records.

Swagger UI available at:
- `http://localhost:8086/q/swagger-ui`

## 3. Source Code Organization

Runtime code:
- `src/main/java/org/acme/EnergyAnalyticsResource.java`: REST resource, schema initialization, and metric query endpoints.
- `src/main/java/org/acme/services/AnalyticsCalculationService.java`: Aggregation logic, persistence, and Kafka publishing.
- `src/main/java/org/acme/entities/EnergyDischargedByZone.java`: Discharged-energy model and database queries.
- `src/main/java/org/acme/entities/GeneratedEnergyByProsumer.java`: Generated-energy model and database queries.
- `src/main/java/org/acme/entities/ConsumedEnergyByProsumer.java`: Consumed-energy model and database queries.
- `src/main/java/org/acme/entities/AverageSoC.java`: Average SoC model and database queries.
- `src/main/java/org/acme/dto/TelemetryDTO.java`: Telemetry input DTO.
- `src/main/java/org/acme/dto/AssetLinkDTO.java`: Asset-link input DTO.
- `src/main/java/org/acme/dto/AnalyticsRequest.java`: Request payload for the evaluate endpoint.
- `src/main/java/org/acme/dto/AnalyticsResult.java`: Result payload returned by the evaluate endpoint.

Configuration:
- `src/main/resources/application.properties`

Local container execution:
- `docker-compose.yml`

## 4. Tests Documentation

### 4.1 Test Suites

Unit-style tests (direct method calls with mocked DB client and mocked service):
- `src/test/java/org/acme/EnergyAnalyticsResourceTest.java`

HTTP integration-style tests (Quarkus runtime with RestAssured and mocked DB client/service):
- `src/test/java/org/acme/EnergyAnalyticsResourceIT.java`

Kafka logic tests with the SmallRye in-memory connector:
- `src/test/java/org/acme/EnergyAnalyticsKafkaTest.java`

Test configuration:
- `src/test/resources/application.properties`

### 4.2 Covered Functional Scenarios

EnergyAnalytics scenarios:
- Get all discharged-energy records.
- Get discharged-energy records by grid cell.
- Get all generated-energy records.
- Get generated-energy records by prosumer.
- Get all consumed-energy records.
- Get consumed-energy records by prosumer.
- Get all average SoC records.
- Evaluate telemetry and asset links to create aggregated metrics.
- Preserve behavior when telemetry contains null measurement fields or unlinked assets.
- Publish the four Kafka messages produced by the aggregation flow.
- Verify Kafka record keys for discharged zone, generated prosumer, consumed prosumer, and average SoC events.

### 4.3 What Is Validated

- REST behavior (status codes and response structure).
- Resource-layer mapping from database rows to domain responses.
- Aggregation request handling and delegated service invocation.
- Correct handling of empty and partial datasets.

### 4.4 How to Run Tests

From this folder (`code/microservices/EnergyAnalytics`):

Run tests:
```sh
./mvnw clean test
```

Run only the Kafka logic test:
```sh
./mvnw test -Dtest=EnergyAnalyticsKafkaTest
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.build=false
```

## 5. Installation and Execution Procedures

### 5.1 Prerequisites

- Java 17
- Docker and Docker Compose (for container run)
- Maven wrapper support
- Kafka broker reachable on the configured bootstrap servers

### 5.2 Run in Dev Mode (Quarkus) - For Development

Fastest iteration with live reload. Requires MySQL and Kafka to be available. Start Docker Compose in a separate terminal first:

```sh
docker compose up
```

Then in another terminal:

```sh
./mvnw compile quarkus:dev
```

Changes are reflected immediately. Access at `http://localhost:8086/q/swagger-ui`

### 5.3 Run with Docker Compose - For Testing

All-in-one containerized setup. No live reload; rebuild required for code changes:

```sh
docker compose up --build
```

Services:
- **EnergyAnalytics API**: `http://localhost:8086`
- **Swagger UI**: `http://localhost:8086/q/swagger-ui`
- **MySQL**: `localhost:3313`
- **Kafka**: `localhost:9092`

## 6. Terraform Scripts and Deployment

EnergyAnalytics Terraform module:
- `../../Quarkus-Terraform/energyanalytics/EC2InstallQuarkus.tf`

EnergyAnalytics startup bootstrap script:
- `../../Quarkus-Terraform/energyanalytics/quarkus.sh`

Shared deployment automation script:
- `../../DeploymentAutomation-macOS.sh`

Shared RDS Terraform module:
- `../../RDS-Terraform/RDSCreation-S3statefile.tf`

### 6.1 EnergyAnalytics Terraform Module Summary

The module:
- Provisions an EC2 instance for EnergyAnalytics deployment.
- Provisions a security group.
- Executes a startup script that installs Docker and runs the EnergyAnalytics container.

## 7. Parametrization

### 7.1 Application Parameters

Defined in `src/main/resources/application.properties`:

- `quarkus.http.port=8086`
- `quarkus.datasource.db-kind=mysql`
- `quarkus.datasource.username=teste`
- `quarkus.datasource.password=testeteste`
- `quarkus.datasource.reactive.url=mysql://localhost:3306/energyanalytics`
- `quarkus.swagger-ui.path=/swagger-ui`
- `quarkus.swagger-ui.always-include=true`
- `myapp.schema.create=true`
- `kafka.bootstrap.servers=localhost:9092`
- `mp.messaging.outgoing.energy-discharged-zone.topic=Energy-Discharged-Zone`
- `mp.messaging.outgoing.energy-generated-prosumer.topic=Energy-Generated-Prosumer`
- `mp.messaging.outgoing.energy-consumed-prosumer.topic=Energy-Consumed-Prosumer`
- `mp.messaging.outgoing.average-soc.topic=Average-SoC`

### 7.2 Test Parameters

Defined in `src/test/resources/application.properties`:

- `myapp.schema.create=false`
- `quarkus.datasource.devservices.enabled=false`
- `quarkus.datasource.reactive.url=vertx-reactive:mysql://localhost:3306/test`
- `quarkus.datasource.username=test`
- `quarkus.datasource.password=test`
- `quarkus.container-image.build=false`

### 7.3 Docker Compose Parameters

Defined in `docker-compose.yml`:

MySQL:
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`

EnergyAnalytics service:
- `QUARKUS_DATASOURCE_REACTIVE_URL`
- `QUARKUS_DATASOURCE_USERNAME`
- `QUARKUS_DATASOURCE_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`

### 7.4 Terraform Parameters

Defined in `../../Quarkus-Terraform/energyanalytics/EC2InstallQuarkus.tf`:

- `variable "security_group_name"`