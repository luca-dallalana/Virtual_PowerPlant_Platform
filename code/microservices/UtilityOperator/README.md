# UtilityOperator Microservice

## 1. Overview

The UtilityOperator microservice manages Utility Operators and Grid Cells in the VPPaaS.
- Create, read, update, and delete utility operators.
- Manage grid cells associated with each operator.
- Persist data in MySQL using Quarkus reactive client and Mutiny.

## 2. API Endpoints

Base path: `/UtilityOperator`

UtilityOperator endpoints:
- `GET /UtilityOperator`
- `GET /UtilityOperator/{id}`
- `POST /UtilityOperator` - Returns 201 with `Location: /UtilityOperator/{generatedId}`
- `DELETE /UtilityOperator/{id}`
- `PUT /UtilityOperator/{id}/{name}/{location}`

GridCell endpoints:
- `GET /UtilityOperator/gridcell`
- `GET /UtilityOperator/gridcell/{gridCellId}`
- `GET /UtilityOperator/{utilityOperatorId}/gridcells`
- `POST /UtilityOperator/gridcell` - Returns 201 with `Location: /UtilityOperator/gridcell/{gridCellId}`
- `DELETE /UtilityOperator/gridcell/{gridCellId}`
- `PUT /UtilityOperator/gridcell/{gridCellId}`
- `PUT /UtilityOperator/gridcell/{gridCellId}/capacity/{maxCapacity}`

Swagger UI available at:
- `http://localhost:8081/q/swagger-ui`


## 3. Source Code Organization

Runtime code:
- `src/main/java/org/acme/UtilityOperatorResource.java`: REST resource and endpoint behavior.
- `src/main/java/org/acme/UtilityOperator.java`: Utility operator model and database operations.
- `src/main/java/org/acme/GridCell.java`: Grid cell model and database operations.

Configuration:
- `src/main/resources/application.properties`

Local container execution:
- `docker-compose.yml`

## 4. Tests Documentation

### 4.1 Test Suites

Unit-style tests (direct method calls with mocked DB client):
- `src/test/java/org/acme/UtilityOperatorResourceTest.java`

HTTP integration-style tests (Quarkus runtime with RestAssured and mocked DB client):
- `src/test/java/org/acme/UtilityOperatorResourceIT.java`

Test configuration:
- `src/test/resources/application.properties`

### 4.2 Covered Functional Scenarios

UtilityOperator scenarios:
- Get all utility operators.
- Get utility operator by id (found and not found).
- Create utility operator (201 + `Location` header with generated operator ID, e.g., `/UtilityOperator/42`).
- Delete utility operator (204 and 404 paths).
- Update utility operator (204 and 404 paths).

GridCell scenarios:
- Get all grid cells.
- Get grid cell by ID (found and not found).
- Get grid cells by operator (non-empty and empty responses).
- Create grid cell (201 + `Location` header).
- Delete grid cell (204 and 404 paths).
- Update grid cell (204 and 404 paths).
- Update grid cell capacity (204 and 404 paths).

### 4.3 What Is Validated

- REST behavior (status codes and response structure).
- Resource layer mapping from DB result to domain response.
- Correct handling of success and not-found outcomes.

### 4.4 How to Run Tests

From this folder (`code/microservices/UtilityOperator`):

Run tests:
```sh
./mvnw clean test
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

Fastest iteration with live reload. Requires MySQL running in a separate terminal:

```sh
docker compose up
```

Then in another terminal:

```sh
./mvnw compile quarkus:dev
```

Changes are reflected immediately. Access at `http://localhost:8081/q/swagger-ui`

### 5.3 Run with Docker Compose — For Testing

All-in-one containerized setup. No live reload; rebuild required for code changes:

```sh
docker compose up --build
```

Services:
- **UtilityOperator API**: `http://localhost:8081`
- **Swagger UI**: `http://localhost:8081/q/swagger-ui`
- **MySQL**: `localhost:3307`

## 6. Terraform Scripts and Deployment

UtilityOperator Terraform module:
- `../../Quarkus-Terraform/utilityoperator/EC2InstallQuarkus.tf`

UtilityOperator startup bootstrap script:
- `../../Quarkus-Terraform/utilityoperator/quarkus.sh`

Shared deployment automation script:
- `../../DeploymentAutomation-macOS.sh`

Shared RDS Terraform module:
- `../../RDS-Terraform/RDSCreation-S3statefile.tf`

### 6.1 UtilityOperator Terraform Module Summary

The module:
- Provisions an EC2 instance for UtilityOperator deployment.
- Provisions a security group.
- Executes a startup script that installs Docker and runs the UtilityOperator container.

## 7. Parametrization

### 7.1 Application Parameters

Defined in `src/main/resources/application.properties`:

- `quarkus.http.port=8081`
- `quarkus.datasource.db-kind=mysql`
- `quarkus.datasource.username=teste`
- `quarkus.datasource.password=testeteste`
- `quarkus.datasource.reactive.url=mysql://localhost:3306/utilityoperator`
- `quarkus.swagger-ui.path=swagger-ui`
- `quarkus.swagger-ui.always-include=true`

### 7.2 Test Properties

Defined in `src/test/resources/application.properties`:

- `quarkus.test.datasource.reactive.url` - Test database URL (typically same MySQL instance)
- Other test-specific overrides

### 7.3 Docker Compose Environment Variables

Defined in `docker-compose.yml`:

```yaml
environment:
  MYSQL_ROOT_PASSWORD: testeteste
  MYSQL_DATABASE: utilityoperator
  MYSQL_USER: teste
  MYSQL_PASSWORD: testeteste
```

### 7.4 Terraform Variables

Defined in `terraform.tfvars` or via `-var` CLI flags:

- `instance_type`: EC2 instance type (default: t4g.small)
- `region`: AWS region (default: us-east-1)
- `environment_tag`: Environment name for tagging resources


