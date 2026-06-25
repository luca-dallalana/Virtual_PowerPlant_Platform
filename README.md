# VPPaaS — Virtual Power Plant as a Service

VPPaaS is an energy management platform that ingests real-time telemetry from prosumer assets (batteries, solar panels, EV chargers), evaluates grid flexibility and balancing conditions through Camunda-orchestrated BPMN processes, and publishes aggregated analytics to downstream Kafka consumers. Eight independent Quarkus microservices communicate exclusively through a Kong API Gateway — no direct service-to-service HTTP calls exist in the system. Business logic lives in BPMN process definitions deployed to Camunda 8, with Zeebe service tasks making every inter-service call through Kong. The full stack — RDS, a 3-broker Kafka KRaft cluster, Camunda, Ollama, Kong, and all eight services — deploys to AWS across two accounts with a single script.

---

## Two Engineering Decisions

### Dynamic Kafka consumer model

SmallRye Reactive Messaging, Quarkus's default Kafka integration, reads topic names from configuration at startup and binds channels at that point. There is no supported path to subscribe to an additional topic after the application is running. That is a hard constraint for VPPaaS: asset owners register their own Kafka topics after the platform is already deployed, so the topic names are unknown at startup time.

The options were: require a service restart for each new topic registration (operationally unacceptable), maintain a single catch-all topic and route within the service (would couple asset owners to a shared namespace), or bypass the framework's consumer lifecycle entirely. VPPaaS takes the third path: a `POST /Telemetry/Consume` request instantiates a `DynamicTopicConsumer extends Thread`, passes it the topic name, Kafka bootstrap servers, and the reactive `MySQLPool`, and calls `worker.start()`. The thread creates a raw `KafkaConsumer<String, String>`, subscribes to the single topic, and runs a `while(true)` poll loop at 100ms intervals. Each polled record is parsed by asset type (`BATTERY`, `SOLAR`, `EV_CHARGER`) and written to MySQL via `client.preparedQuery(query).execute(params).await().indefinitely()`.

The trade-offs are concrete and worth naming. The poll loop has no interrupt path, calling `Thread.interrupt()` on the worker has no effect because nothing checks the interrupted flag. Any exception inside the loop is caught and printed (`System.out.println`), then the loop continues, meaning a persistent failure produces no alerting and no backpressure. The `.await().indefinitely()` call inside a raw thread blocks on each database write, which means write latency directly stalls message consumption for that topic. All dynamically spawned consumers also share the hardcoded `group.id="your-group-id"`, so adding a second consumer to the same topic triggers a Kafka rebalance rather than independent consumption. What this model does deliver is full runtime flexibility with no framework ceremony, a topic can be registered with a single curl call against a running service, and the consumer is active within milliseconds.

### BPMN deployment patching pipeline

Camunda 8's BPMN process files embed service task URLs as literal strings inside the XML. Those URLs must point to Kong's address, which is only known after Terraform provisions the Kong EC2 instance. Similarly, Kafka connector tasks embed the bootstrap server address. The BPMN files cannot be parameterized at the Camunda level, what gets deployed is the XML as written.

The deployment pipeline solves this with a bidirectional sed/regex cycle. BPMN files are committed with two placeholder strings: `KONG_SERVER_PLACEHOLDER` for service task base URLs and `KAFKA_SERVER_PLACEHOLDER:9092` for Kafka bootstrap addresses. After Terraform completes, `DeploymentAutomation-macOS.sh` extracts the live EC2 DNS names from `terraform state show`, then runs `sed -i '' "s|KONG_SERVER_PLACEHOLDER|${addressKong}|g"` across all BPMN files, followed by the Kafka equivalent. The patched files are uploaded to Camunda's deployment API. For subsequent deploys, `RestoreBPMN.sh` regexes the EC2 DNS patterns back to placeholders (`ec2-[0-9]*-[0-9]*-[0-9]*-[0-9]*\.compute-1\.amazonaws\.com`) before the next patch cycle. The Kafka restore must run before the Kong restore because the Kafka addresses carry a `:9092` suffix that the Kong regex would otherwise partially match.

This is fragile by design: the round-trip depends on EC2 DNS names matching the expected pattern, on sed succeeding silently if a placeholder is already replaced, and on the restore script running before every redeploy. The alternative was templating tools like Helm or a custom Camunda connector that resolves addresses at runtime. Both required infrastructure not available in the target environment. In practice the pipeline has been reliable for the deployment sequence documented below, but it is not suited to any topology where EC2 DNS names diverge from the `compute-1.amazonaws.com` pattern.

---

## Architecture

No service shares a schema or reads another service's tables directly. All orchestration is externalized to Camunda 8 BPMN processes, where Zeebe service tasks call each service through Kong. Kong is the only ingress point, Camunda, the event producer, and all BPMN processes address services exclusively through the Kong proxy on port 8000.

| Service | Port | Database | Core responsibility | Kafka outbound |
|---|---|---|---|---|
| Prosumer | 8080 | prosumer | Register prosumers and their assets | — |
| UtilityOperator | 8080 | utilityoperator | Manage utility operators and grid cells | — |
| AssetLink | 8080 | assetlink | Link prosumer assets to operators and grid cells | — |
| Telemetry | 8080 | telemetry | Ingest asset telemetry via dynamic Kafka consumers | — |
| FlexibilityEvent | 8080 | flexibilityevent | Evaluate per-asset flexibility via DMN; publish offers | `Flexibility-Offers` |
| GridBalancingRecommendation | 8080 | gridbalancingrecommendation | Compute net load and headroom per grid cell | `GridBalancingRecommendation` |
| EnergyAnalytics | 8080 | energyanalytics | 30-min sliding window aggregations | `Energy-Discharged-Zone`, `Energy-Generated-Prosumer`, `Energy-Consumed-Prosumer`, `Average-SoC` |
| FlexibilityForecasting | 8080 | flexibilityforecasting | Build LLM prompts from event history; persist forecasts | — |

**FlexibilityEvent DMN rules.** A DMN decision table (`Decision_FlexibilityOffer`) evaluates each active battery asset against three ordered rules: SoC ≤ 20% → `BALANCING_UNAVAILABLE`; SoH < 70% → `DEGRADED_ASSET`; SoH ≥ 70% AND market price `HIGH` AND SoC ≥ 90% → `ARBITRAGE_SELL`. Assets not matching any rule receive `NO_ACTION` and are filtered before the Kafka publish step.

**EnergyAnalytics window.** The 30-minute sliding window averages power readings within the window and multiplies by 0.5 h to produce kWh figures. Results are grouped by prosumer (solar generation, EV consumption) and by grid zone (battery discharge). Each BPMN run pushes computed results to four Kafka topics.

**GridBalancing formula.** Net load per zone = `max(0, EV demand + battery charging − solar generation − battery discharge)`. Headroom = `maxCapacity − netLoad`. A DMN table maps headroom and load conditions to a recommended cross-zone load shift action.

---

## AI-Assisted Forecasting Pipeline

The FlexibilityForecasting BPMN runs on a 20-minute timer. It fetches recent FlexibilityEvent logs, then iterates over each event in a multi-instance subprocess. For each event, a service task calls `POST /FlexibilityForecasting/build-prompt`, which constructs a structured prompt string from the event context: asset ID, event type, SoC and SoH at event time, market price level, current output in kW, current status, and grid cell. The prompt instructs the model to respond with exactly three lines — `SENTIMENT: POSITIVE|NEGATIVE|NEUTRAL`, `SUCCESS: YES|NO`, `REASONING: one short sentence` — and to produce no other output.

That prompt string is sent to the Ollama API (`POST /api/generate`, model `llama3.2`) via a Zeebe HTTP connector. Ollama runs on a dedicated EC2 instance and is proxied through Kong with a 300-second timeout. The raw response text is collected per event. A BPMN script task then processes the collected responses: it counts `YES` occurrences for the success rate, determines dominant sentiment by majority vote, and joins the analyzed event IDs as a comma-separated string. A final service task persists the aggregated result, success rate, dominant sentiment, total events analyzed, event ID list, via `POST /FlexibilityForecasting/forecast`.

This is a structured prompt pipeline, not a trained model or fine-tuned classifier. The LLM's output is parsed by string matching against fixed response templates. The accuracy of the success rate and sentiment aggregation depends entirely on the model following the response format consistently, which it does not always do. There is no validation of the raw response before the script task attempts to parse it.

---

## Infrastructure and Deployment

The platform runs across two AWS accounts. Account 1 holds shared infrastructure: an RDS MySQL instance (`db.t4g.micro`), a 3-broker Kafka cluster in KRaft mode (`t3.small`, Kafka 4.1.1), Camunda 8 (`c8run 8.8.9`), an Ollama instance (`llama3.2`), Kong Gateway (port 8000, admin 8001), and the Konga dashboard (port 1337). Account 2 holds the eight Quarkus microservices, each on its own `t4g.small` ARM EC2 instance (AMI `ami-0bb7267a511c0a8e8`). The split was driven by per-account EC2 instance quotas in the target environment; it maps cleanly onto a real infrastructure-account/workload-account boundary.

Each component has its own Terraform configuration in `code/`. Infrastructure is provisioned sequentially: RDS first (the connection string is needed by all services), then Kafka, then Camunda, Ollama, Kong, and Konga on Account 1, then the eight services on Account 2. After all EC2 DNS names are known, `DeploymentAutomation-macOS.sh` runs the BPMN patching cycle described above and uploads the patched files to Camunda. Kong routes are provisioned by `kongCommands-Provisioning.sh`, which registers all nine services (8 microservices + Ollama) with regex path patterns and `strip_path=false`.

For single-service updates, `HotRedeploy.sh` resolves the target service's EC2 DNS from `terraform state show` without touching Terraform state, SSHes into the instance, and runs `docker stop / rm / run` with the new image. No `terraform taint` or instance recreation is needed.

All Kafka topics are created with 3 partitions and replication factor 3. The local development environment replaces the KRaft cluster with a Docker Compose file running 3 brokers on ports 29092, 29093, 29094 with ZooKeeper (`KAFKA_MIN_INSYNC_REPLICAS: 2`, `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3`).

---

## Getting Started

### Prerequisites

- Java 17, Maven Wrapper (`./mvnw` included per service)
- Docker and Docker Compose
- Local MySQL instance on port 3306, credentials `teste` / `testeteste`

### Start the local Kafka cluster

From `code/`:

```bash
docker-compose up -d
```

Three brokers start on ports 29092, 29093, 29094.

### Start a microservice

From `code/microservices/<ServiceName>/`:

```bash
./mvnw compile quarkus:dev
```

Each service auto-creates its database schema on startup. Run one terminal per service.

### Register a Kafka consumer at runtime

```bash
curl -X POST http://localhost:8080/Telemetry/Consume \
  -H "Content-Type: application/json" \
  -d '{"TopicName": "asset-telemetry-1001"}'
```

Then start the event producer in `VPPaaS-EventProducer/` pointing at the same topic and `localhost:29092`.

### Run tests

From any service directory:

```bash
./mvnw clean test
```

Integration tests run against a real local MySQL instance (not mocked). Each service's `src/test/README.md` documents which tests exist and what SQL they verify.

---

## Cloud Deployment

### Prerequisites

- Terraform installed
- Two active AWS accounts with credentials set in `code/access.sh`
- `key.pem` (Account 1 key pair) placed in `code/Kafka/`
- `mskey.pem` (Account 2 key pair) placed in `code/`

### Full deployment

From `code/`:

```bash
./DeploymentAutomation-macOS.sh
```

The script provisions all infrastructure across both accounts in order, patches and uploads BPMN files, builds and pushes Docker images, provisions Kong routes, and runs smoke tests.

### Redeploy a single service without Terraform

```bash
./HotRedeploy.sh <ServiceName>
```

### Restore BPMN placeholders before redeploying

```bash
./RestoreBPMN.sh
```

Run this before any `DeploymentAutomation` invocation that follows a previous deployment. The script regexes EC2 DNS names back to `KONG_SERVER_PLACEHOLDER` and `KAFKA_SERVER_PLACEHOLDER`, restoring the files to a deployable state.

### Tear down

```bash
./UndeploymentAutomation-all.sh
```

---

## Kafka Topics

| Topic | Producer |
|---|---|
| `Flexibility-Offers` | FlexibilityEvent |
| `GridBalancingRecommendation` | GridBalancingRecommendation |
| `Energy-Discharged-Zone` | EnergyAnalytics |
| `Energy-Generated-Prosumer` | EnergyAnalytics |
| `Energy-Consumed-Prosumer` | EnergyAnalytics |
| `Average-SoC` | EnergyAnalytics |

All topics are created with 3 partitions and replication factor 3 by `code/Kafka/topics.sh`.
