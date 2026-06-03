#!/bin/bash

esc=$'\e'

cd KongTerraform
KONG="$(terraform state show aws_instance.exampleInstallKong |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g")"
cd ..

P="http://${KONG}:8000"

echo "----Kong invocation tests for VPPaaS -----"
echo "Kong proxy: $P"
echo ""

echo "=== GET /Prosumer ==="
curl -s -X GET "$P/Prosumer"

echo -e "\n=== GET /Prosumer/AssetBatteries ==="
curl -s -X GET "$P/Prosumer/AssetBatteries"

echo -e "\n=== GET /Prosumer/assets/active ==="
curl -s -X GET "$P/Prosumer/assets/active"

echo -e "\n=== GET /Prosumer/assets/active/SOLAR ==="
curl -s -X GET "$P/Prosumer/assets/active/SOLAR"

echo -e "\n=== GET /UtilityOperator ==="
curl -s -X GET "$P/UtilityOperator"

echo -e "\n=== GET /UtilityOperator/gridcells ==="
curl -s -X GET "$P/UtilityOperator/gridcells"

echo -e "\n=== GET /AssetLink ==="
curl -s -X GET "$P/AssetLink"

echo -e "\n=== GET /Telemetry ==="
curl -s -X GET "$P/Telemetry"

echo -e "\n=== GET /Telemetry/window/SOLAR/20 ==="
curl -s -X GET "$P/Telemetry/window/SOLAR/20"

echo -e "\n=== GET /FlexibilityEvent ==="
curl -s -X GET "$P/FlexibilityEvent"

echo -e "\n=== GET /FlexibilityEvent/logs ==="
curl -s -X GET "$P/FlexibilityEvent/logs"

echo -e "\n=== GET /GridBalancingRecommendation/recommendations ==="
curl -s -X GET "$P/GridBalancingRecommendation/recommendations"

echo -e "\n=== GET /EnergyAnalytics/discharged-by-zone ==="
curl -s -X GET "$P/EnergyAnalytics/discharged-by-zone"

echo -e "\n=== GET /EnergyAnalytics/generated-by-prosumer ==="
curl -s -X GET "$P/EnergyAnalytics/generated-by-prosumer"

echo -e "\n=== GET /EnergyAnalytics/consumed-by-prosumer ==="
curl -s -X GET "$P/EnergyAnalytics/consumed-by-prosumer"

echo -e "\n=== GET /EnergyAnalytics/average-soc ==="
curl -s -X GET "$P/EnergyAnalytics/average-soc"

echo -e "\n=== GET /FlexibilityForecasting/history ==="
curl -s -X GET "$P/FlexibilityForecasting/history"

echo -e "\n=== GET /api/tags (Ollama) ==="
curl -s -X GET "$P/api/tags"

echo -e "\n=== POST /api/generate (Ollama test prompt) ==="
curl -s -X POST "$P/api/generate" \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3.2","prompt":"Say hello in one word.","stream":false}'

echo ""
echo "----Kong invocation tests complete -----"
