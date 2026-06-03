#!/bin/bash

# Restores KONG_SERVER_PLACEHOLDER in BPMN files before a fresh deployment.
# The deployment script replaces the placeholder with the live Kong DNS each run,
# so this must be run before redeploying to allow the new Kong address to be patched in.

BPMN_DIR="./BPMN"

if ! grep -q "amazonaws\.com" "$BPMN_DIR"/*.bpmn 2>/dev/null; then
    echo "BPMN files are already clean — KONG_SERVER_PLACEHOLDER is in place."
    exit 0
fi

echo "Restoring KONG_SERVER_PLACEHOLDER in BPMN files..."
sed -i '' 's/ec2-[0-9]*-[0-9]*-[0-9]*-[0-9]*\.compute-1\.amazonaws\.com/KONG_SERVER_PLACEHOLDER/g' "$BPMN_DIR"/*.bpmn

if grep -q "amazonaws\.com" "$BPMN_DIR"/*.bpmn 2>/dev/null; then
    echo "ERROR: Some EC2 addresses still remain in BPMN files — check manually."
    exit 1
fi

echo "Done. All BPMN files restored."
