#!/bin/bash

# Restores KONG_SERVER_PLACEHOLDER and KAFKA_SERVER_PLACEHOLDER in BPMN files
# before a fresh deployment. The deployment script replaces placeholders with
# live EC2 DNS each run, so this must be run before redeploying.
#
# Kafka bootstrap servers appear as bare "hostname:9092" (no http scheme).
# Kong URLs appear as "http://hostname:8000/..." so the hostname alone is matched
# after Kafka is already restored. Order matters: restore :9092 first.

BPMN_DIR="./BPMN"

EC2_PATTERN="ec2-[0-9]*-[0-9]*-[0-9]*-[0-9]*\.compute-1\.amazonaws\.com"

if ! grep -qE "$EC2_PATTERN" "$BPMN_DIR"/*.bpmn 2>/dev/null; then
    echo "BPMN files are already clean — placeholders are in place."
    exit 0
fi

echo "Restoring KAFKA_SERVER_PLACEHOLDER in BPMN files..."
sed -i '' "s/${EC2_PATTERN}:9092/KAFKA_SERVER_PLACEHOLDER/g" "$BPMN_DIR"/*.bpmn

echo "Restoring KONG_SERVER_PLACEHOLDER in BPMN files..."
sed -i '' "s/${EC2_PATTERN}/KONG_SERVER_PLACEHOLDER/g" "$BPMN_DIR"/*.bpmn

if grep -qE "$EC2_PATTERN" "$BPMN_DIR"/*.bpmn 2>/dev/null; then
    echo "ERROR: Some EC2 addresses still remain in BPMN files — check manually."
    exit 1
fi

echo "Done. All BPMN files restored."
