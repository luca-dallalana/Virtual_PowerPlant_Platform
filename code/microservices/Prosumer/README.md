# Prosumer Microservice

## 1. Overview

The Prosumer microservice manages Prosumers and Assets in the VPPaaS.
- Create, read, update, and delete prosumers.
- List and manage assets associated with each prosumer.
- Persist data in MySQL using Quarkus reactive client and Mutiny.

## 2. API Endpoints

Base path: `/Prosumer`

Prosumer endpoints:
- `GET /Prosumer`
- `GET /Prosumer/{id}`
- `POST /Prosumer`
- `DELETE /Prosumer/{id}`
- `PUT /Prosumer/{id}/{name}/{FiscalNumber}/{location}`

Asset endpoints:
- `GET /Prosumer/{prosumerId}/assets`
- `POST /Prosumer/{prosumerId}/assets`
- `DELETE /Prosumer/{prosumerId}/assets/{assetId}`
- `PUT /Prosumer/{prosumerId}/assets/{assetId}/status/{status}`

Swagger UI available at:
- `/swagger-ui`

## 3. Source Code Organization

Runtime code:
- `src/main/java/org/acme/ProsumerResource.java`: REST resource and endpoint behavior.
- `src/main/java/org/acme/Prosumer.java`: prosumer model and database operations.
- `src/main/java/org/acme/Asset.java`: asset model and database operations.

Configuration:
- `src/main/resources/application.properties`

Local container execution:
- `docker-compose.yml`

## 4. Tests Documentation

### 4.1 Test Suites

Unit-style tests (direct method calls with mocked DB client):
- `src/test/java/org/acm/ProsumerResourceTest.java`

HTTP integration-style tests (Quarkus runtime with RestAssured and mocked DB client):
- `src/test/java/org/acm/ProsumerResourceIT.java`

Test configuration:
- `src/test/resources/application.properties`

### 4.2 Covered Functional Scenarios

Prosumer scenarios:
- Get all prosumers.
- Get prosumer by id (found and not found).
- Create prosumer (201 + `Location` header).
- Delete prosumer (204 and 404 paths).
- Update prosumer (204 and 404 paths).

Asset scenarios:
- Get all assets for a prosumer (non-empty and empty responses).
- Create asset (201 + `Location` header).
- Delete asset (204 and 404 paths).
- Update asset status (204 and 404 paths).

### 4.3 What Is Validated

- REST behavior (status codes and response structure).
- Resource layer mapping from DB result to domain response.
- Correct handling of success and not-found outcomes.

### 4.4 How to Run Tests

From this folder (`code/microservices/Prosumer`):

Run tests:
```sh
./mvnw clean test
```

Run full verification lifecycle:
```sh
./mvnw clean verify
```

## 5. Installation and Execution Procedures

### 5.1 Prerequisites

- Java 17
- Docker and Docker Compose (for container run)
- Maven wrapper support

### 5.2 Run in Dev Mode (Quarkus)

```sh
./mvnw compile quarkus:dev
```

Default HTTP port is `8080`.

### 5.3 Run with Docker Compose

```sh
docker compose up --build
```

Service mapping:
- `mysql` at `3306`
- `prosumer` at `8080`

## 6. Terraform Scripts and Deployment

Prosumer Terraform module:
- `../../Quarkus-Terraform/prosumer/EC2InstallQuarkus.tf`

Prosumer startup bootstrap script:
- `../../Quarkus-Terraform/prosumer/quarkus.sh`

Shared deployment automation script:
- `../../DeploymentAutomation-macOS.sh`

Shared RDS Terraform module:
- `../../RDS-Terraform/RDSCreation-S3statefile.tf`

### 6.1 Prosumer Terraform Module Summary

The module:
- Provisions an EC2 instance for Prosumer deployment.
- Provisions a security group.
- Executes a startup script that installs Docker and runs the Prosumer container.

## 7. Parametrization

### 7.1 Application Parameters

Defined in `src/main/resources/application.properties`:

- `quarkus.http.port=8080`
- `quarkus.datasource.db-kind=mysql`
- `quarkus.datasource.username=teste`
- `quarkus.datasource.password=testeteste`
- `quarkus.datasource.reactive.url=mysql://localhost:3306/prosumer`
- `quarkus.swagger-ui.path=swagger-ui`
- `quarkus.swagger-ui.always-include=true`
- `quarkus.container-image.build=true`
- `quarkus.container-image.push=true`

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

Prosumer service:
- `QUARKUS_DATASOURCE_REACTIVE_URL`
- `QUARKUS_DATASOURCE_USERNAME`
- `QUARKUS_DATASOURCE_PASSWORD`

### 7.4 Terraform Parameters

Defined in `../../Quarkus-Terraform/prosumer/EC2InstallQuarkus.tf`:

- `variable "security_group_name"` with default `terraform-Quarkus-instance-prosumer2026`

