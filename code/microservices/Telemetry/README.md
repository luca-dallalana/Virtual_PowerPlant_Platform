# Telemetry Microservice

## 1. Overview

The Telemetry microservice manages telemetry records for assets and grid cells in the VPPaaS system.
- List telemetry entries and fetch individual entries by id.
- Start a Kafka consumer worker to ingest telemetry from a topic.
- Persist data in MySQL using the Quarkus reactive client and Mutiny.

## 2. API Endpoints

Base path: `/Telemetry`

Telemetry endpoints:
- `GET /Telemetry` - List all telemetry records
- `GET /Telemetry/{id}` - Get a telemetry record by id
- `POST /Telemetry/Consume` - Start a consumer worker for a Kafka topic

Swagger UI available at:
- `http://localhost:8083/q/swagger-ui`

## 3. Source Code Organization

Runtime code:
- `src/main/java/org/acme/KafkaProvisioningResource.java`: REST resource, schema initialization, and Kafka consumer trigger.
- `src/main/java/org/acme/Telemetry.java`: telemetry model and database queries.
- `src/main/java/org/acme/DynamicTopicConsumer.java`: Kafka consumer worker that processes incoming telemetry.
- `src/main/java/org/acme/model/Topic.java`: request payload used to start a consumer worker.

Configuration:
- `src/main/resources/application.properties`

Local container execution:
- `docker-compose.yml`

## 4. Tests Documentation

### 4.1 Test Suites

Unit-style tests (direct method calls with mocked DB client):
- `src/test/java/org/acme/KafkaProvisioningResourceTest.java`

HTTP integration-style tests (Quarkus runtime with RestAssured and mocked DB client):
- `src/test/java/org/acme/KafkaProvisioningResourceIT.java`

Test configuration:
- `src/test/resources/application.properties`

### 4.2 Covered Functional Scenarios

Telemetry scenarios:
- Get all telemetry records.
- Get telemetry by id (found and not found).
- Start a consumer worker with `POST /Telemetry/Consume`.
- Validate telemetry row mapping from MySQL result rows.

### 4.3 What Is Validated

- REST behavior (status codes and response structure).
- Resource-layer mapping from database rows to domain responses.
- Worker start request handling and malformed JSON behavior.

### 4.4 How to Run Tests

From this folder (`code/microservices/Telemetry`):

Run tests:
```sh
./mvnw clean test
```

Run full verification lifecycle:
```sh
./mvnw clean verify -Dquarkus.container-image.build=false
```

## 5. Installation and Execution Procedures

### 5.1 Prerequisites

- Java 17
- Docker and Docker Compose
- Maven wrapper support
- Kafka broker reachable on the configured bootstrap servers

### 5.2 Run in Dev Mode (Quarkus)

Requires MySQL and Kafka to be available. Start Docker Compose in a separate terminal first:

```sh
docker compose up
```

Then run dev mode in another terminal:

```sh
./mvnw compile quarkus:dev
```

Default HTTP port is `8083`. Live reload enabled for code changes. Swagger UI is available at `http://localhost:8083/q/swagger-ui`.

### 5.3 Run with Docker Compose

```sh
docker compose up --build
```

Services:
- **Telemetry API**: `http://localhost:8083`
- **Swagger UI**: `http://localhost:8083/q/swagger-ui`
- **MySQL**: `localhost:3309`

## 6. Terraform Scripts and Deployment

Telemetry Terraform module:
- `../../Quarkus-Terraform/telemetry/EC2InstallQuarkus.tf`

Telemetry startup bootstrap script:
- `../../Quarkus-Terraform/telemetry/quarkus.sh`

Shared deployment automation script:
- `../../DeploymentAutomation-macOS.sh`

Shared RDS Terraform module:
- `../../RDS-Terraform/RDSCreation-S3statefile.tf`

### 6.1 Telemetry Terraform Module Summary

The module:
- Provisions an EC2 instance for Telemetry deployment.
- Provisions a security group.
- Executes a startup script that installs Docker and runs the Telemetry container.

## 7. Parametrization

### 7.1 Application Parameters

Defined in `src/main/resources/application.properties`:

- `quarkus.http.port=8083`
- `quarkus.datasource.db-kind=mysql`
- `quarkus.datasource.username=teste`
- `quarkus.datasource.password=testeteste`
- `quarkus.datasource.reactive.url=mysql://localhost:3306/telemetry`
- `quarkus.swagger-ui.path=swagger-ui`
- `quarkus.swagger-ui.always-include=true`
- `quarkus.container-image.build=true`
- `quarkus.container-image.push=true`

### 7.2 Test Parameters

Defined in `src/test/resources/application.properties`:

- `myapp.schema.create=false`
- `kafka.bootstrap.servers=localhost:19092`

### 7.3 Docker Compose Parameters

Defined in `docker-compose.yml`:

MySQL:
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`

Telemetry service:
- `QUARKUS_DATASOURCE_REACTIVE_URL`
- `QUARKUS_DATASOURCE_USERNAME`
- `QUARKUS_DATASOURCE_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`

### 7.4 Terraform Parameters

Defined in `../../Quarkus-Terraform/telemetry/EC2InstallQuarkus.tf`:

- `variable "security_group_name"` with default `terraform-Quarkus-telemetry-2026`
