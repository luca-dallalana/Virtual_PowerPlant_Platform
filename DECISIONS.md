# Architectural Decision Records

---

## 1. Dynamic Kafka consumer model

**Problem.** Asset owners register their own Kafka topics through the platform's REST API after the system is already running. The topic names are unknown at service startup time.

**Alternatives considered.**
- SmallRye Reactive Messaging with static configuration: requires topic names in `application.properties` at build time. No supported runtime extension path.
- Restart the Telemetry service for each new topic registration: operationally unacceptable for a platform that must stay live.
- Single shared inbound topic with an internal routing key: couples all asset producers to a shared namespace and requires coordination around key uniqueness.
- MicroProfile Reactive Messaging programmatic consumer API: not yet stable in the Quarkus 3.x release used.

**Choice.** Bypass SmallRye entirely. A REST endpoint accepts a topic name, instantiates `DynamicTopicConsumer extends Thread` with the topic name and a reference to the reactive `MySQLPool`, and calls `worker.start()`. The thread creates a raw `KafkaConsumer<String, String>`, subscribes, and runs a continuous poll loop at 100ms intervals. Each record is parsed by asset type and written to MySQL.

**Known trade-offs.**
- No graceful shutdown: the poll loop has no interrupt path. Calling `Thread.interrupt()` has no observable effect. Stopping a consumer requires killing the JVM process.
- Failures are swallowed: exceptions inside the loop are caught and printed, then the loop continues. A persistent failure (database down, deserialization error) produces log noise and silent data loss, not alerting or backpressure.
- Blocking writes per record: `.await().indefinitely()` inside the raw thread blocks on each database insert. Database write latency directly stalls message consumption for that topic with no buffering.
- Shared `group.id`: all dynamically spawned consumers use the hardcoded `group.id="your-group-id"`. Spawning a second consumer for the same topic triggers Kafka partition rebalancing rather than independent parallel consumption.

---

## 2. Database-per-service

**Problem.** Eight microservices must each store and query their own domain data without coupling their schemas to one another's internals.

**Alternatives considered.**
- Shared MySQL database with per-service schemas: reduces infrastructure footprint but creates implicit coupling — one service's migration can break another's query if foreign key assumptions shift.
- Single schema, single database: harder to reason about which service owns which table; breaks independent deployability.

**Choice.** Each service auto-creates its own MySQL database on startup using `MySQLPool` DDL. Database names are service-specific (`prosumer`, `telemetry`, `flexibilityevent`, etc.). No service holds a reference to another service's database name.

**Known trade-offs.**
- Cross-service queries are impossible at the database level. Any data combination (e.g., joining asset data from AssetLink with telemetry readings) must go through REST calls orchestrated at the BPMN level.
- Schema migration is per-service and uncoordinated. The `myapp.schema.create=true` flag drops and recreates tables on every startup in the current configuration — acceptable for a deployed-from-scratch model, not for any in-place upgrade scenario.
- In production, eight separate RDS logical databases run on a single `db.t4g.micro` instance, which shares resources across all services. This works at current load but means one service's write pressure affects all others.

---

## 3. Camunda orchestration vs. event choreography

**Problem.** Business processes — flexibility evaluation, grid balancing, energy analytics, forecasting — involve sequences of calls across multiple services with conditional branching (DMN decisions, gateway logic, user tasks). The coordination logic had to live somewhere.

**Alternatives considered.**
- Event choreography: each service listens to Kafka topics and reacts by producing events that trigger the next service. No central coordinator. Operationally harder to trace end-to-end process state; debugging a multi-step failure requires correlating events across all service logs.
- Saga pattern with compensating transactions: more appropriate for distributed transactions. The processes here are read-heavy with a single write at the end; full saga machinery would be overhead without benefit.
- Camunda 8 with BPMN 2.0: externalized orchestration with visual process definitions, built-in DMN evaluation, user task support, and an audit log of process instance state.

**Choice.** Camunda 8 with Zeebe service tasks. All business orchestration lives in BPMN files deployed to Camunda. Service tasks call microservices through Kong using the `io.camunda:http-json:1` connector. Kafka publishing uses `io.camunda:connector-kafka:1`.

**Known trade-offs.**
- BPMN files embed service URLs as literal strings. Addresses are only known post-Terraform, requiring the patching pipeline described in Decision 6.
- Camunda 8 (c8run) is a heavy runtime: it requires a dedicated EC2 instance and does not run well on anything smaller than a `t3.medium`. It dominates infrastructure cost relative to the eight microservices.
- Any change to process logic requires redeploying the BPMN file to Camunda, not redeploying a service. This is a different release artifact from the Java services and requires operators to know the Camunda deployment API.

---

## 4. Kong API Gateway as the sole service ingress

**Problem.** Eight microservices each run on separate EC2 instances with separate DNS names. Callers — Camunda service tasks, the event producer, external test clients — would otherwise need to know and maintain the address of each individual service.

**Alternatives considered.**
- Direct service-to-service HTTP: each caller knows the target's address. Any IP/DNS change requires updating every caller. No central point for auth, rate limiting, or observability.
- Service mesh (e.g., Istio): appropriate for large clusters with mutual TLS requirements. Significant operational overhead for eight services.
- AWS API Gateway: managed, but adds AWS-specific coupling and requires re-provisioning on every address change.

**Choice.** Kong Gateway on a single EC2 instance, provisioned by `kongCommands-Provisioning.sh`. Nine services are registered (8 microservices + Ollama). Routes use regex path patterns with `strip_path=false`. Camunda service tasks address all services through `http://<KONG_HOST>:8000/<ServiceName>/...`.

**Known trade-offs.**
- Kong is a single point of failure for the entire platform. If the Kong instance goes down, all Camunda processes fail at the first service task call.
- The Konga dashboard (port 1337) exposes Kong's admin API through a UI with no authentication configured in this deployment.
- Ollama's 300-second timeout is configured at the Kong route level. This is necessary for Llama 3.2 inference latency but means a hung Ollama request blocks that Kong connection slot for 5 minutes.

---

## 5. Ollama / Llama 3.2 for flexibility forecasting

**Problem.** The FlexibilityForecasting process needed to evaluate past flexibility events and produce a structured assessment (success rate, dominant sentiment) across a batch of events.

**Alternatives considered.**
- Statistical model (e.g., logistic regression on SoC, SoH, market price level): would produce consistent, deterministic outputs and require no GPU/LLM infrastructure. Requires labeled training data and a training pipeline, neither of which was available.
- Pre-trained transformer via API (e.g., a hosted model): adds an external dependency, API cost, and network latency to every forecast run.
- Rule-based classifier: `ARBITRAGE_SELL` events above a SoC threshold → `POSITIVE`. Deterministic but inflexible to edge cases the DMN rules don't capture.

**Choice.** Ollama running Llama 3.2 on a self-hosted EC2 instance. The BPMN builds a structured prompt per event using `POST /FlexibilityForecasting/build-prompt`, sends it to Ollama's `/api/generate` endpoint via Kong, and parses the three-line response. Aggregation (success rate, dominant sentiment) is computed in a BPMN script task over the collected responses.

**Known trade-offs.**
- Llama 3.2 does not reliably follow the response format. The parser assumes exact lines `SENTIMENT: X`, `SUCCESS: X`, `REASONING: X`. Any deviation from that format produces a silent parse failure, not an exception.
- The LLM has no knowledge of energy domain conventions beyond what is embedded in the prompt. Its `SUCCESS` and `SENTIMENT` labels reflect the model's interpretation of the prompt framing, not ground-truth event outcomes.
- Ollama on `t3.medium` (or comparable) runs inference in CPU-only mode. A 20-minute BPMN timer with 10+ events in the iteration can take several minutes of wall-clock time just on LLM calls.
- This is a structured prompt pipeline, not a trained model. It does not generalize beyond the prompt template.

---

## 6. Two-AWS-account infrastructure split

**Problem.** The full platform requires: RDS, 3 Kafka brokers, Camunda, Ollama, Kong, Konga, and 8 Quarkus service instances. That is 14+ EC2 instances plus RDS.

**Alternatives considered.**
- Single AWS account: simpler credential management. Exceeds the per-account EC2 instance quota in the target environment (AWS Academy Learner Lab, which enforces per-account limits and uses temporary STS credentials).
- ECS or EKS for the 8 microservices: would consolidate instances onto fewer EC2 nodes. Outside the scope of available tooling and operational familiarity for this project.

**Choice.** Two AWS Academy accounts. Account 1 holds all shared infrastructure (RDS, Kafka, Camunda, Ollama, Kong, Konga). Account 2 holds the 8 Quarkus microservices. `code/access.sh` exports AWS credentials for each account via `use_account1()` / `use_account2()` shell functions. `DeploymentAutomation-macOS.sh` calls these functions to switch contexts between the infrastructure and service provisioning phases.

**Known trade-offs.**
- Credentials must be refreshed manually. AWS Academy sessions expire and issue new STS tokens; `access.sh` must be updated before each deployment session.
- Cross-account communication relies on public EC2 DNS names. All services — Kafka bootstrap addresses, Kong URLs — are addressed by hostname rather than VPC-internal IPs, so all traffic traverses the public internet.
- `key.pem` and `mskey.pem` must be placed manually in the correct directories before deployment. The script has no guard for missing key files.
- The split maps structurally onto an infra-account/workload-account boundary pattern used in real multi-account AWS organizations, but was driven by quota constraints rather than a deliberate security boundary decision.
