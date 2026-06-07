#!/bin/bash
# KongRedeploy.sh
# Wipes existing Kong config and re-runs kongCommands-Provisioning.sh.
# Does NOT touch any EC2 instances or run terraform apply.
#
# Usage: ./KongRedeploy.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/access.sh"

esc=$'\e'

# Get Kong admin address from Terraform state
KONG_SERVER="$(terraform -chdir="$SCRIPT_DIR/KongTerraform" state show aws_instance.exampleInstallKong 2>/dev/null \
    | grep ' public_dns ' \
    | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" | sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g")"
KONG_ADMIN="http://$KONG_SERVER:8001"
echo "Kong Admin: $KONG_ADMIN"

# Delete all routes for a service, then the service itself
delete_service() {
    local service=$1
    local route_ids
    route_ids=$(curl -s "$KONG_ADMIN/services/$service/routes" | jq -r '.data[].id' 2>/dev/null)
    for id in $route_ids; do
        curl -s -X DELETE "$KONG_ADMIN/routes/$id" > /dev/null
    done
    curl -s -X DELETE "$KONG_ADMIN/services/$service" > /dev/null
    echo "  Removed $service"
}

echo ""
echo "=== Wiping existing Kong configuration ==="
for svc in prosumer-service utilityoperator-service assetlink-service \
            telemetry-service flexibilityevent-service gridbalancing-service \
            energyanalytics-service flexforecasting-service ollama-service; do
    delete_service "$svc"
done

echo ""
echo "=== Running kongCommands-Provisioning.sh ==="
bash "$SCRIPT_DIR/kongCommands-Provisioning.sh"
