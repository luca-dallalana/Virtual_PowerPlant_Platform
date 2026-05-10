# AssetLink Microservice

## 1. Overview

The AssetLink microservice manages the associations between Prosumers and Utility Operators in the VPPaaS (Virtual Power Plant as a Service) system.
- Create, read, and delete links between prosumers and utility operators.
- Persist data in MySQL using Quarkus reactive client and Mutiny.

## 2. API Endpoints

Base path: `/AssetLink`

AssetLink endpoints:
- `GET /AssetLink` - List all asset links
- `GET /AssetLink/{id}` - Get a specific link by ID
- `GET /AssetLink/findByProsumerAndOperator/{prosumerId}/{operatorId}` - Query links by prosumer and operator pair
- `POST /AssetLink` - Create a new asset link. Returns 201 with `Location: /AssetLink/{generatedId}`
- `DELETE /AssetLink/{id}` - Delete an asset link
- `PUT /AssetLink/{id}/{idProsumer}/{idUtilityOperator}` - Update an asset link

Swagger UI available at:
- `http://localhost:8082/q/swagger-ui`

## 3. Source Code Organization

Runtime code:
- `src/main/java/org/acme/AssetLinkResource.java`: REST resource and endpoint behavior.
- `src/main/java/org/acme/AssetLink.java`: Asset link model and database operations.

Configuration:
- `src/main/resources/application.properties`

Local container execution:
- `docker-compose.yml`

## 4. Tests Documentation

### 4.1 Test Suites

Unit-style tests (direct method calls with mocked DB client):
- `src/test/java/org/acme/AssetLinkTest.java`

HTTP integration-style tests (Quarkus runtime with RestAssured and mocked DB client):
- `src/test/java/org/acme/AssetLinkResourceIT.java`

Test configuration:
- `src/test/resources/application.properties`

### 4.2 Covered Functional Scenarios

AssetLink scenarios:
- Get all asset links.
- Get asset link by ID (found and not found).
- Get asset link by (prosumerId, operatorId) pair (found and not found).
- Create asset link (201 + `Location` header with generated asset link ID, e.g., `/AssetLink/42`).
- Delete asset link (204 and 404 paths).
- Update asset link (204 and 404 paths).
- Unique constraint validation (cannot create duplicate prosumer-operator pairs).

### 4.3 What Is Validated

- REST behavior (status codes and response structure).
- Resource layer mapping from DB result to domain response.
- Correct handling of success and not-found outcomes.
- Unique constraint enforcement on (idProsumer, idUtilityOperator) pairs.

### 4.4 How to Run Tests

From this folder (`code/microservices/AssetLink`):

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

Changes are reflected immediately. Access at `http://localhost:8082/q/swagger-ui`

### 5.3 Run with Docker Compose — For Testing

All-in-one containerized setup. No live reload; rebuild required for code changes:

```sh
docker compose up --build
```

Services:
- **AssetLink API**: `http://localhost:8082`
- **Swagger UI**: `http://localhost:8082/q/swagger-ui`
- **MySQL**: `localhost:3308`

## 6. Terraform Scripts and Deployment

AssetLink Terraform module:
- `../../Quarkus-Terraform/assetlink/EC2InstallQuarkus.tf`

AssetLink startup bootstrap script:
- `../../Quarkus-Terraform/assetlink/quarkus.sh`

Shared deployment automation script:
- `../../DeploymentAutomation-macOS.sh`

Shared RDS Terraform module:
- `../../RDS-Terraform/RDSCreation-S3statefile.tf`

### 6.1 AssetLink Terraform Module Summary

The module:
- Provisions an EC2 instance for AssetLink deployment.
- Provisions a security group.
- Executes a startup script that installs Docker and runs the AssetLink container.

## 7. Parametrization

### 7.1 Application Parameters

Defined in `src/main/resources/application.properties`:

- `quarkus.http.port=8082`
- `quarkus.datasource.db-kind=mysql`
- `quarkus.datasource.username=teste`
- `quarkus.datasource.password=testeteste`
- `quarkus.datasource.reactive.url=mysql://localhost:3306/assetlink`
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
  MYSQL_DATABASE: assetlink
  MYSQL_USER: teste
  MYSQL_PASSWORD: testeteste
```

### 7.4 Terraform Variables

Defined in `terraform.tfvars` or via `-var` CLI flags:

- `instance_type`: EC2 instance type (default: t4g.small)
- `region`: AWS region (default: us-east-1)
- `environment_tag`: Environment name for tagging resources
