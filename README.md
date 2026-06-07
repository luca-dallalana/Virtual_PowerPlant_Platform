# VPPaaS — Virtual Power Plant as a Service

IST Enterprise Integration — Group 9

A distributed microservices platform for real-time energy management. The system ingests telemetry from prosumer assets (batteries, solar panels, EV chargers), evaluates flexibility and grid balancing conditions through Camunda-orchestrated BPMN processes, and publishes aggregated analytics to downstream Kafka consumers. Eight independent Quarkus services communicate through a Kong API Gateway, with all business orchestration driven by Camunda 8 BPMN processes.

---

## Repository Structure

```
SEI_project/
├── code/
│   ├── microservices/          # 8 Quarkus microservices
│   │   ├── Prosumer/
│   │   ├── UtilityOperator/
│   │   ├── AssetLink/
│   │   ├── Telemetry/
│   │   ├── FlexibilityEvent/
│   │   ├── GridBalancingRecommendation/
│   │   ├── EnergyAnalytics/
│   │   └── FlexibilityForecasting/
│   ├── BPMN/                   # Camunda BPMN and DMN process definitions
│   ├── Kafka/                  # Kafka topic creation scripts and Terraform
│   ├── Quarkus-Terraform/      # Per-service EC2 provisioning (one folder per service)
│   ├── KongTerraform/          # Kong API Gateway EC2 provisioning
│   ├── KongaTerraform/         # Konga dashboard provisioning
│   ├── Camunda-Terraform/      # Camunda Engine EC2 provisioning
│   ├── OllamaTerraform/        # Ollama LLM EC2 provisioning
│   ├── RDS-Terraform/          # AWS RDS MySQL provisioning
│   ├── docker-compose.yml      # Local Kafka cluster (3 brokers + Zookeeper)
│   ├── kongCommands-Provisioning.sh     # Registers all routes on Kong
│   ├── kongCommands-Invocation-Tests.sh # Smoke tests through Kong
│   ├── DeploymentAutomation-macOS.sh    # Full cloud deploy from macOS
│   ├── DeploymentAutomation-ubuntu.sh   # Full cloud deploy from Ubuntu
│   ├── HotRedeploy.sh          # Rebuild and redeploy a single service
│   ├── UndeploymentAutomation-all.sh    # Tear down all infrastructure
│   └── access.sh               # SSH shortcuts for EC2 instances
├── VPPaaS-EventProducer/       # Simulator: produces Kafka telemetry messages
├── UML/                        # Sequence diagrams (PNG + UML source) per service
├── SEI_Report.md               # Project report
└── README.md                   # This file
```

---

## Microservices

Each service is an independent Quarkus application with its own MySQL database. All use Java 17, reactive `MySQLPool` + Mutiny, and expose a REST API.

| Service | Port | Database | Kafka (outbound topics) |
|---|---|---|---|
| Prosumer | 8080 | prosumer | — |
| UtilityOperator | 8081 | utilityoperator | — |
| AssetLink | 8082 | assetlink | — |
| Telemetry | 8083 | telemetry | dynamic (registered at runtime) |
| FlexibilityEvent | 8084 | flexibilityevent | `Flexibility-Offers` |
| GridBalancingRecommendation | 8085 | gridbalancingrecommendation | `GridBalancingRecommendation` |
| EnergyAnalytics | 8086 | energyanalytics | `Energy-Discharged-Zone`, `Energy-Generated-Prosumer`, `Energy-Consumed-Prosumer`, `Average-SoC` |
| FlexibilityForecasting | 8087 | flexibilityforecasting | — |

---

## Running Locally

### Prerequisites

- Java 17
- Docker and Docker Compose
- MySQL (local instance or via a local container)
- Maven Wrapper (`./mvnw`) included in each service directory

### 1. Start the Kafka cluster

From `code/`:

```bash
docker-compose up -d
```

This starts a 3-broker Kafka cluster (ports 29092, 29093, 29094) and Zookeeper.

### 2. Start each microservice

From any service directory under `code/microservices/<ServiceName>/`:

```bash
./mvnw compile quarkus:dev
```

Each service auto-creates its database schema on startup (`myapp.schema.create=true`). The default datasource credentials are `teste` / `testeteste` against `localhost:3306`.

To start all services, open a terminal per service (or use a process manager). Each runs on its own fixed port (see table above).

### 3. Running the event producer simulator

The `VPPaaS-EventProducer/` directory contains a standalone Java application that produces telemetry messages in the format expected by the Telemetry service. Run it after registering a Kafka topic consumer via:

```bash
POST /Telemetry/Consume
{"TopicName": "<your-topic>"}
```

Then start the producer pointing at the same topic and `localhost:29092`.

---

## Running Tests

From any service directory:

```bash
# Unit tests
./mvnw clean test

# Integration tests (QuarkusTest + RestAssured)
./mvnw clean test -Dtest=<ServiceName>ResourceIT

# Both
./mvnw clean test -Dtest="<ServiceName>Test,<ServiceName>ResourceIT"
```

Each `src/test/README.md` inside the service documents which tests exist and what SQL they verify.

---

## Cloud Deployment (AWS)

### Prerequisites

- AWS CLI configured
- Terraform installed
- An active AWS account with sufficient EC2 and RDS quotas

### Automated deployment

From `code/`:

```bash
# macOS
./DeploymentAutomation-macOS.sh

# Ubuntu/Linux
./DeploymentAutomation-ubuntu.sh
```

These scripts run `terraform apply` for each component in order: RDS → Kafka → Kong → Konga → Ollama → per-service EC2. After infrastructure is up, Kong routes are registered via:

```bash
./kongCommands-Provisioning.sh
```

### Manual order (if running Terraform steps individually)

1. `RDS-Terraform/` — provisions the shared MySQL RDS instance
2. `Kafka/` — provisions the Kafka EC2 cluster and creates all required topics
3. `KongTerraform/` — provisions the Kong API Gateway instance
4. `KongaTerraform/` — provisions the Konga admin dashboard
5. `OllamaTerraform/` — provisions the Ollama LLM instance (required by FlexibilityForecasting)
6. `Quarkus-Terraform/<service>/` — provisions one EC2 instance per microservice
7. `Camunda-Terraform/` — provisions the Camunda engine instance

### BPMN deployment

After Camunda is running, deploy the process definitions from `code/BPMN/` through the Camunda Modeler or REST API. The BPMN files define the orchestration flows that drive Flexibility Emission, Grid Balancing, Energy Analytics, and Forecasting.

### Redeploying a single service

```bash
./HotRedeploy.sh <ServiceName>
```

### Tearing down everything

```bash
./UndeploymentAutomation-all.sh
```

---

## Kafka Topics

| Topic | Producer service |
|---|---|
| `Flexibility-Offers` | FlexibilityEvent |
| `GridBalancingRecommendation` | GridBalancingRecommendation |
| `Energy-Discharged-Zone` | EnergyAnalytics |
| `Energy-Generated-Prosumer` | EnergyAnalytics |
| `Energy-Consumed-Prosumer` | EnergyAnalytics |
| `Average-SoC` | EnergyAnalytics |

All topics are created with 3 partitions and replication factor 3 by `code/Kafka/topics.sh`.
