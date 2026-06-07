#!/bin/bash

# JAVA path (macOS) — adjust for your platform if running on Linux
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export PATH=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin:"$PATH"

# Docker credentials
yourDockerUsername=
yourDockerPassword=
export DockerUsername=$yourDockerUsername
export DockerPassword=$yourDockerPassword

# ── Account 1: infrastructure (RDS, Kafka, Camunda, Ollama, Kong, Konga) ──────
account1_access_key_id=
account1_secret_access_key=
account1_session_token=

# ── Account 2: services (all 8 Quarkus microservices) ─────────────────────────
account2_access_key_id=
account2_secret_access_key=
account2_session_token=

use_account1() {
    export AWS_ACCESS_KEY_ID=$account1_access_key_id
    export AWS_SECRET_ACCESS_KEY=$account1_secret_access_key
    export AWS_SESSION_TOKEN=$account1_session_token
    echo "[AWS] Switched to Account 1 (infrastructure)"
}

use_account2() {
    export AWS_ACCESS_KEY_ID=$account2_access_key_id
    export AWS_SECRET_ACCESS_KEY=$account2_secret_access_key
    export AWS_SESSION_TOKEN=$account2_session_token
    echo "[AWS] Switched to Account 2 (services)"
}

# Default to Account 1
use_account1
