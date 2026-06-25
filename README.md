# VPPaaS — Virtual Power Plant as a Service

IST Enterprise Integration — Group 9

A distributed microservices platform for real-time energy management. The system ingests telemetry from prosumer assets (batteries, solar panels, EV chargers), evaluates flexibility and grid balancing conditions through Camunda-orchestrated BPMN processes, and publishes aggregated analytics to downstream Kafka consumers. Eight independent Quarkus services communicate through a Kong API Gateway, with all business orchestration driven by Camunda 8 BPMN processes.

---

## Tech Stack

**Backend**
- Java 17, Quarkus 3.27.2 (reactive REST with RESTEasy Jackson)
- Mutiny + Vert.x reactive MySQL client (non-blocking DB access throughout)

**Database**
- MySQL — one database per service, auto-created on startup via `MySQLPool`
- AWS RDS (`db.t4g.micro`) in production

**Messaging**
- Apache Kafka — 3-broker cluster, topics with 3 partitions and replication factor 3

**Orchestration**
- Camunda 8 (c8run 8.8.9) — BPMN 2.0 processes and DMN decision tables
- Zeebe service tasks make HTTP calls to microservices through Kong

**API Gateway**
- Kong Gateway (proxy on port 8000) + Konga dashboard (port 1337)

**AI / LLM**
- Ollama running Llama 3.2 on an EC2 instance (port 11434)
- Used by the FlexibilityForecasting BPMN to evaluate past flexibility events

**Infrastructure**
- AWS EC2 (us-east-1), AWS RDS, Terraform (one `.tf` file per component)
- Docker — each microservice is packaged as a container image and deployed with `docker run`

**Testing**
- JUnit 5, RestAssured, Mockito (`mockito-inline`)

---

## Features

- Ingest real-time telemetry from heterogeneous prosumer assets (BATTERY, SOLAR, EV_CHARGER) via dynamically provisioned Kafka consumer threads — each registered at runtime via a REST call, no static config binding
- Evaluate battery flexibility per asset using BPMN-driven rules: emit `ARBITRAGE_SELL` when SoC > 90%, or `BALANCING_UNAVAILABLE` when SoC < 20%, based on DMN decision tables
- Compute grid balancing recommendations per grid cell by calculating net load (demand − supply) and headroom against a configurable capacity threshold, with cross-zone load shift recommendations
- Aggregate energy analytics over a 30-minute sliding window: solar generation per prosumer, EV consumption per prosumer, battery discharge per zone, and fleet-wide average SoC
- AI-assisted flexibility forecasting: build structured LLM prompts from event context, evaluate them against a self-hosted Llama 3.2 instance via Ollama, and persist forecast outcomes (success rate, dominant sentiment, event IDs)
- Orchestrate all business logic through Camunda 8 BPMN processes with Zeebe service tasks, user tasks, and exclusive gateways backed by DMN decisions
- Route all external traffic through Kong API Gateway with automated service/route provisioning and end-to-end smoke tests
- One-command cloud deployment: provision all infrastructure (EC2, RDS, Kafka, Ollama, Kong, Camunda, 8 microservices), patch BPMN files with live addresses, build and push Docker images, configure Kong, and upload processes to Camunda in a single script

---

## Architecture / How It Works

Each microservice owns its own MySQL database (database-per-service pattern) and exposes a REST API. There is no direct service-to-service HTTP communication — all orchestration is externalized to Camunda 8 BPMN processes, which call each service through Kong using Zeebe HTTP service tasks.

**Telemetry ingestion** uses a dynamic thread model. Quarkus's SmallRye Reactive Messaging would require topic names at startup, which conflicts with the requirement to register topics at runtime. Instead, a `POST /Telemetry/Consume` request spawns a raw `KafkaConsumer` thread (`DynamicTopicConsumer`) for the given topic. That thread polls continuously, parses each message's JSON payload by asset type (BATTERY / SOLAR / EV_CHARGER), and inserts into the Telemetry table using the reactive MySQL client.

**Energy analytics** computes a 30-minute sliding window: for each asset, it averages the power readings within the window and multiplies by 0.5 h to get kWh. Results are grouped by prosumer (solar generation, EV consumption) and by grid zone (battery discharge). Each BPMN run pushes computed results to four Kafka topics for downstream consumers.

**Grid balancing** per zone: net load = max(0, EV demand + battery charging − solar generation − battery discharge); headroom = maxCapacity − netLoad. A DMN table maps headroom and load conditions to a recommended action (e.g., shift charging load from PORTO-IN to LISBON-DT).

**Flexibility forecasting** is the AI-integrated process. The BPMN fetches recent `FlexibilityEvent` logs, iterates over each event, calls `POST /FlexibilityForecasting/build-prompt` to construct a structured LLM prompt with event context (SoC at event time, SoH, market price level, current output), sends that prompt to Ollama's `llama3.2` model, parses the three-line response (`SENTIMENT / SUCCESS / REASONING`), and finally calls `POST /FlexibilityForecasting/forecast` to persist an aggregated result with success rate and dominant sentiment.

**Deployment automation** (`DeploymentAutomation-macOS.sh`) runs the full pipeline in sequence across two AWS accounts: provision infrastructure with Terraform, extract live EC2 DNS names from `terraform state show`, patch the BPMN XML files in-place with `sed` to replace `KONG_SERVER_PLACEHOLDER` and `KAFKA_SERVER_PLACEHOLDER`, build and push Docker images via `mvnw clean package`, provision each microservice's EC2 instance, configure Kong routes, and upload BPMN/DMN/form files to Camunda's deployment API.

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
| UtilityOperator | 8080 | utilityoperator | — |
| AssetLink | 8080 | assetlink | — |
| Telemetry | 8080 | telemetry | dynamic (registered at runtime) |
| FlexibilityEvent | 8080 | flexibilityevent | `Flexibility-Offers` |
| GridBalancingRecommendation | 8080 | gridbalancingrecommendation | `GridBalancingRecommendation` |
| EnergyAnalytics | 8080 | energyanalytics | `Energy-Discharged-Zone`, `Energy-Generated-Prosumer`, `Energy-Consumed-Prosumer`, `Average-SoC` |
| FlexibilityForecasting | 8080 | flexibilityforecasting | — |

---

## Getting Started

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

## Usage

All requests below go through the Kong proxy (`http://<KONG_HOST>:8000`). In local dev, call the service directly on its port instead.

**Register a Kafka telemetry consumer at runtime**

```bash
curl -X POST http://localhost:8080/Telemetry/Consume \
  -H "Content-Type: application/json" \
  -d '{"TopicName": "my-asset-topic"}'
```

**Query flexibility events from the last 20 minutes**

```bash
curl http://<KONG_HOST>:8000/FlexibilityEvent/logs/20
```

**Compute grid cell metrics and retrieve balancing recommendations**

```bash
# Get all recommendations issued in the last 20 minutes
curl http://<KONG_HOST>:8000/GridBalancingRecommendation/recommendations/20

# Compute real-time metrics for a specific grid cell with telemetry payload
curl -X POST http://<KONG_HOST>:8000/GridBalancingRecommendation/metrics \
  -H "Content-Type: application/json" \
  -d '{"gridCell":{"gridCellId":"PORTO-IN","maxCapacity":75},"telemetryData":[]}'
```

**Build a forecasting prompt and send it to Ollama**

```bash
curl -X POST http://<KONG_HOST>:8000/FlexibilityForecasting/build-prompt \
  -H "Content-Type: application/json" \
  -d '{"eventId":1,"assetId":1001,"eventType":"ARBITRAGE_SELL","recommendedAction":"DISCHARGE","socAtEventTime":95.2,"sohAtEventTime":92.5,"marketPriceLevel":"HIGH","gridCellId":"LISBON-DT","currentSoc":90.9,"currentOutputKw":5.5,"currentStatus":"ONLINE"}'
# Returns a ready-to-send prompt string for Ollama's /api/generate endpoint
```

---

## Cloud Deployment (AWS)

### Prerequisites

- AWS CLI configured
- Terraform installed
- An active AWS account with sufficient EC2 and RDS quotas

### Automated deployment

Its needed to set access.sh using your credentials 

From account 1, you need to download the pem key and name it key.pem, and place it on the /kafka directory

From account 2, you need to download the pem key and name it mskey.pem, and place it on the /code directory


From `code/`:

```bash
# macOS
./DeploymentAutomation-macOS.sh
```

### Redeploying a single service

```bash
./HotRedeploy.sh <ServiceName>
```

### Tearing down everything

```bash
./UndeploymentAutomation-all.sh
```

### Before redeploying, make sure to run 

```bash
./RestoreBPMN.sh
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

---

## What I Learned / Challenges

The hardest coordination problem was making BPMN processes work against a fully dynamic cloud deployment. BPMN files embed the Kong and Kafka hostnames directly in service task URLs, but those addresses are only known after Terraform provisions the EC2 instances, so the deployment script had to extract addresses from `terraform state show`, patch the BPMN XML in-place with `sed`, and then upload the modified files to Camunda's deployment API, all in a single ordered sequence across two AWS accounts. Getting that sequencing right and keeping the BPMN files restorable (via `RestoreBPMN.sh`) for subsequent deploys took significant iteration.

The second challenge was dynamic Kafka topic subscription in Quarkus. Quarkus's SmallRye Reactive Messaging reads topic names from configuration at startup and cannot add new topics at runtime, which is incompatible with a platform where asset owners register their own Kafka topics after the system is already running. The solution was to bypass the framework entirely: a REST endpoint accepts a topic name and spawns a raw `KafkaConsumer` thread that subscribes to that topic and writes records directly to MySQL using the reactive `MySQLPool`. This gave full runtime flexibility at the cost of managing thread lifecycle manually.
