#!/bin/bash
# Usage:
#   ./HotRedeploy.sh                         → rebuilds and redeploys ALL microservices
#   ./HotRedeploy.sh <ServiceName>           → rebuilds and redeploys ONE microservice
#
# Available service names:
#   Telemetry, AssetLink, Prosumer, UtilityOperator,
#   FlexibilityEvent, GridBalancingRecommendation,
#   EnergyAnalytics, FlexibilityForecasting
#
# Prerequisites: infrastructure must already be running (i.e. full DeploymentAutomation ran at least once).

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/access.sh"
use_account2

KEY_FILE="$SCRIPT_DIR/mskey.pem"
chmod 400 "$KEY_FILE" 2>/dev/null

esc=$'\e'

# Parallel arrays (bash 3 compatible — macOS ships bash 3.2)
SERVICES=(Telemetry AssetLink Prosumer UtilityOperator FlexibilityEvent GridBalancingRecommendation EnergyAnalytics FlexibilityForecasting)
TF_DIRS=(telemetry assetlink prosumer utilityoperator flexibilityevent gridbalancingrecommendation energyanalytics flexibilityforecasting)

get_tf_dir() {
    local service
    service="$(echo "$1" | tr '[:upper:]' '[:lower:]')"
    for i in "${!SERVICES[@]}"; do
        local svc
        svc="$(echo "${SERVICES[$i]}" | tr '[:upper:]' '[:lower:]')"
        if [[ "$svc" == "$service" ]]; then
            echo "${TF_DIRS[$i]}"
            return 0
        fi
    done
    return 1
}

get_ec2_dns() {
    local tf_dir=$1
    terraform -chdir="$SCRIPT_DIR/Quarkus-Terraform/$tf_dir" state show aws_instance.exampleDeployQuarkus 2>/dev/null \
        | grep ' public_dns ' \
        | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" | sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"
}

redeploy_service() {
    local service=$1
    local tf_dir
    tf_dir=$(get_tf_dir "$service")

    if [[ $? -ne 0 ]]; then
        echo "ERROR: Unknown service '$service'."
        echo "Valid names: ${SERVICES[*]}"
        exit 1
    fi

    echo ""
    echo "========================================"
    echo " Redeploying: $service"
    echo "========================================"

    # 1. Resolve running EC2 address from Terraform state (no instance recreation)
    echo "[1/3] Fetching EC2 address from Terraform state..."
    local ec2_dns
    ec2_dns=$(get_ec2_dns "$tf_dir")
    if [[ -z "$ec2_dns" ]]; then
        echo "ERROR: No running EC2 instance found for '$service'."
        echo "       Run the full DeploymentAutomation first."
        exit 1
    fi
    echo "      EC2: $ec2_dns"

    # 2. Build new Docker image and push to DockerHub
    echo "[2/3] Building and pushing Docker image..."
    local ms_dir="$SCRIPT_DIR/microservices/$service"
    local docker_image docker_version
    docker_image="$(grep -m 1 "<artifactId>" "$ms_dir/pom.xml" \
        | sed "s/<artifactId>//g" | sed "s/<\/artifactId>//g" | sed "s/\"//g" | sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g")"
    docker_version="$(grep -m 1 "<version>" "$ms_dir/pom.xml" \
        | sed "s/<version>//g" | sed "s/<\/version>//g" | sed "s/\"//g" | sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g")"

    (cd "$ms_dir" && ./mvnw clean package)

    # 3. SSH into the running EC2, pull new image, restart container (no EC2 recreation)
    # Each command is on its own line so || true only covers the command it's on,
    # not the entire chain (avoids the bash && / || precedence trap).
    echo "[3/3] Hot-swapping container on EC2..."
    ssh -i "$KEY_FILE" \
        -o StrictHostKeyChecking=no \
        -o ConnectTimeout=30 \
        ec2-user@"$ec2_dns" << REMOTE
set -e
sudo service docker start 2>/dev/null || true
sudo docker login -u "$DockerUsername" -p "$DockerPassword"
sudo docker pull $DockerUsername/$docker_image:$docker_version
sudo docker stop $docker_image 2>/dev/null || true
sudo docker rm   $docker_image 2>/dev/null || true
sudo docker run -d --name $docker_image -p 8080:8080 $DockerUsername/$docker_image:$docker_version
REMOTE

    echo ""
    echo "Done! $service is live at:"
    echo "  http://$ec2_dns:8080/q/swagger-ui/"
}

# ─── Main ─────────────────────────────────────────────────────────────────────

if [[ $# -eq 0 ]]; then
    echo "No service specified — redeploying ALL microservices."
    for service in "${SERVICES[@]}"; do
        redeploy_service "$service"
    done
else
    redeploy_service "$1"
fi
