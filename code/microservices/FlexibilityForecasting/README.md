# FlexibilityForecasting Microservice

## 1. Overview

The FlexibilityForecasting microservice analyzes flexibility event and balancing recommendation data using a Large Language Model (Ollama) in the VPPaaS.
- Analyze past flexibility events and related balancing recommendations.
- Determine the sentiment of operational outcomes.
- Estimate the success rate of past actions, such as whether a `Discharge` command resulted in a successful `Grid Stable` state.
- Persist analysis history in MySQL using the Quarkus reactive client and Mutiny.

## 2. API Endpoints

Base path: `/FlexibilityForecasting`

Analysis endpoints:
- `POST /FlexibilityForecasting/analyze`
- `POST /FlexibilityForecasting/analyze/sentiment`
- `POST /FlexibilityForecasting/analyze/success-rate`

History endpoints:
- `GET /FlexibilityForecasting/history`
- `GET /FlexibilityForecasting/history/{id}`
- `GET /FlexibilityForecasting/history/type/{analysisType}`
- `DELETE /FlexibilityForecasting/history/{id}`

Swagger UI available at:
- `http://localhost:8087/swagger-ui`

## 3. Source Code Organization

Runtime code:
- `src/main/java/org/acme/FlexibilityForecastingResource.java`: REST resource, analysis endpoints, and history behavior.
- `src/main/java/org/acme/services/ForecastingService.java`: Orchestrates Ollama calls, event correlation, parsing, and persistence.
- `src/main/java/org/acme/services/DataCorrelationService.java`: Matches flexibility events with balancing recommendations and computes success metrics.
- `src/main/java/org/acme/services/PromptBuilder.java`: Builds the structured prompt sent to Ollama.
- `src/main/java/org/acme/entities/FlexibilityForecast.java`: Forecast history model and database operations.
- `src/main/java/org/acme/clients/OllamaService.java`: REST client used to call the Ollama API.
- `src/main/java/org/acme/dto/*.java`: Request, response, and correlation DTOs.

Configuration:
- `src/main/resources/application.properties`

Local container execution:
- `docker-compose.yml`
- `src/main/docker/Dockerfile.jvm`

## 4. Tests Documentation

### 4.1 Test Suites

Unit-style tests (direct method calls with mocked DB client and service dependencies):
- `src/test/java/org/acme/FlexibilityForecastingResourceTest.java`

HTTP integration-style tests (Quarkus runtime with RestAssured and mocked dependencies):
- `src/test/java/org/acme/FlexibilityForecastingResourceIT.java`

Test configuration:
- `src/test/resources/application.properties`

### 4.2 Covered Functional Scenarios

Analysis scenarios:
- Run a general analysis request.
- Run sentiment analysis.
- Run success-rate analysis with and without filters.

History scenarios:
- Get all forecast history.
- Get forecast history by id (found and not found).
- Get forecast history by analysis type.
- Delete forecast history (204 and 404 paths).

### 4.3 What Is Validated

- REST behavior (status codes and response structure).
- Resource-layer mapping from database results to forecast history responses.
- Correct orchestration of analysis requests through the forecasting service.
- Success and not-found outcomes for stored analysis history.

### 4.4 How to Run Tests

From this folder (`code/microservices/FlexibilityForecasting`):

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
- Docker and Docker Compose
- Ollama available locally or through the provided container setup
- The `llama3.2:latest` model pulled in Ollama

### 5.2 Run in Dev Mode (Quarkus) — For Development

Fastest iteration with live reload. Requires MySQL and Ollama running in separate terminals:

```sh
docker compose up mysql ollama ollama-init
```

Then in another terminal:

```sh
./mvnw compile quarkus:dev
```

Changes are reflected immediately. Access at `http://localhost:8087/swagger-ui`

### 5.3 Run with Docker Compose — For Testing

All-in-one containerized setup. No live reload; rebuild required for code changes:

```sh
docker compose up --build
```

Services:
- **FlexibilityForecasting API**: `http://localhost:8087`
- **Swagger UI**: `http://localhost:8087/swagger-ui`
- **MySQL**: `localhost:3312`
- **Ollama**: `http://localhost:11434`

## 6. Terraform Scripts and Deployment

FlexibilityForecasting Terraform module:
- `../../Quarkus-Terraform/flexibilityforecasting/EC2InstallQuarkus.tf`

FlexibilityForecasting startup bootstrap script:
- `../../Quarkus-Terraform/flexibilityforecasting/quarkus-ollama.sh`

Shared deployment automation script:
- `../../DeploymentAutomation-macOS.sh`

### 6.1 FlexibilityForecasting Terraform Module Summary

The module:
- Provisions an EC2 instance for the FlexibilityForecasting deployment.
- Provisions a security group.
- Executes a startup script that installs Docker, starts Ollama, pulls the `llama3.2:latest` model, and runs the FlexibilityForecasting container.

## 7. Parametrization

### 7.1 Application Parameters

Defined in `src/main/resources/application.properties`:

- `quarkus.http.port=8087`
- `quarkus.datasource.db-kind=mysql`
- `quarkus.datasource.username=teste`
- `quarkus.datasource.password=testeteste`
- `quarkus.datasource.reactive.url=mysql://localhost:3306/flexibilityforecasting`
- `myapp.schema.create=true`
- `quarkus.swagger-ui.always-include=true`
- `quarkus.swagger-ui.path=/swagger-ui`
- `quarkus.rest-client.ollama-api.url=http://localhost:11434`
- `quarkus.rest-client.ollama-api.scope=jakarta.inject.Singleton`
- `quarkus.rest-client.ollama-api.read-timeout=60000`
- `quarkus.rest-client.ollama-api.connect-timeout=5000`
- `ollama.model=llama3.2:latest`
- `ollama.temperature=0.7`
- `ollama.max-tokens=1000`
- `forecast.max-events=200`
- `forecast.correlation-window-minutes=30`

### 7.2 Test Parameters

Defined in `src/test/resources/application.properties`:

- `myapp.schema.create=false`
- `quarkus.rest-client.ollama-api.url=http://localhost:11434`
- `ollama.model=llama3.2:latest`
- `ollama.temperature=0.7`
- `ollama.max-tokens=1000`
- `forecast.max-events=200`
- `forecast.correlation-window-minutes=30`

### 7.3 Docker Compose Parameters

Defined in `docker-compose.yml`:

MySQL:
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`

FlexibilityForecasting service:
- `QUARKUS_DATASOURCE_REACTIVE_URL`
- `QUARKUS_DATASOURCE_USERNAME`
- `QUARKUS_DATASOURCE_PASSWORD`
- `QUARKUS_REST_CLIENT_OLLAMA_API_URL`
- `OLLAMA_MODEL`
- `OLLAMA_TEMPERATURE`
- `OLLAMA_MAX_TOKENS`

### 7.4 Terraform Parameters

Defined in `../../Quarkus-Terraform/flexibilityforecasting/EC2InstallQuarkus.tf`:

- `variable "security_group_name"` with default `terraform-Quarkus-instance-flexibilityforecasting2026`

## 8. Implementation Notes

- Event/recommendation correlation is performed by `DataCorrelationService` using grid-cell matching and a configurable time window in minutes.
- Success rate is only calculated for `SUCCESS_RATE` analyses when a `recommendedAction` filter is provided.
- `OllamaService` calls the Ollama REST endpoint at `/api/generate` and sends prompts produced by `PromptBuilder`.
- `PromptBuilder` requires Ollama to return a single JSON object with fields such as `summary`, `sentiment`, `confidence_score`, `key_patterns`, `risks`, `recommendations`, `data_quality_notes`, and `statistics`.
- Ollama responses are parsed into a `ForecastResult`, parsing failures are captured in the `insights` payload as a parse error.
- Forecast history is persisted in the `FlexibilityForecast` table, storing the analysis type, question, response, sentiment, confidence score, correlation counts, success rate, timestamp, and serialized insights.
- Stored analysis history is returned through `/history`, `/history/{id}`, and `/history/type/{analysisType}`, ordered by `analysisTimestamp` descending for list queries.
