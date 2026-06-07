#!/bin/bash

esc=$'\e'

echo "----Starting Kong provisioning for VPPaaS -----"

# collect addresses from Terraform state
cd KongTerraform
KONG_SERVER_ADDRESS="$(terraform state show aws_instance.exampleInstallKong |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g")"
cd ..

cd Quarkus-Terraform/prosumer
PROSUMER_URL="http://$(terraform state show aws_instance.exampleDeployQuarkus |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):8080"
cd ../..

cd Quarkus-Terraform/utilityoperator
UTILITYOPERATOR_URL="http://$(terraform state show aws_instance.exampleDeployQuarkus |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):8080"
cd ../..

cd Quarkus-Terraform/assetlink
ASSETLINK_URL="http://$(terraform state show aws_instance.exampleDeployQuarkus |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):8080"
cd ../..

cd Quarkus-Terraform/telemetry
TELEMETRY_URL="http://$(terraform state show aws_instance.exampleDeployQuarkus |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):8080"
cd ../..

cd Quarkus-Terraform/flexibilityevent
FLEXIBILITYEVENT_URL="http://$(terraform state show aws_instance.exampleDeployQuarkus |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):8080"
cd ../..

cd Quarkus-Terraform/gridbalancingrecommendation
GRIDBALANCING_URL="http://$(terraform state show aws_instance.exampleDeployQuarkus |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):8080"
cd ../..

cd Quarkus-Terraform/energyanalytics
ENERGYANALYTICS_URL="http://$(terraform state show aws_instance.exampleDeployQuarkus |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):8080"
cd ../..

cd Quarkus-Terraform/flexibilityforecasting
FLEXFORECASTING_URL="http://$(terraform state show aws_instance.exampleDeployQuarkus |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):8080"
cd ../..

cd OllamaTerraform
OLLAMA_URL="http://$(terraform state show 'aws_instance.exampleOllamaConfiguration[0]' |grep public_dns | sed "s/public_dns//g" | sed "s/=//g" | sed "s/\"//g" |sed "s/ //g" | sed "s/$esc\[[0-9;]*m//g"):11434"
cd ..

echo "KONG SERVER ADDRESS = "$KONG_SERVER_ADDRESS
echo "PROSUMER URL        = "$PROSUMER_URL
echo "UTILITYOPERATOR URL = "$UTILITYOPERATOR_URL
echo "ASSETLINK URL       = "$ASSETLINK_URL
echo "TELEMETRY URL       = "$TELEMETRY_URL
echo "FLEXIBILITYEVENT URL= "$FLEXIBILITYEVENT_URL
echo "GRIDBALANCING URL   = "$GRIDBALANCING_URL
echo "ENERGYANALYTICS URL = "$ENERGYANALYTICS_URL
echo "FLEXFORECASTING URL = "$FLEXFORECASTING_URL
echo "OLLAMA URL          = "$OLLAMA_URL


# create services
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
--data "name=prosumer-service" \
--data "url=${PROSUMER_URL}"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
--data "name=utilityoperator-service" \
--data "url=${UTILITYOPERATOR_URL}"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
--data "name=assetlink-service" \
--data "url=${ASSETLINK_URL}"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
--data "name=telemetry-service" \
--data "url=${TELEMETRY_URL}"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
--data "name=flexibilityevent-service" \
--data "url=${FLEXIBILITYEVENT_URL}"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
--data "name=gridbalancing-service" \
--data "url=${GRIDBALANCING_URL}"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
--data "name=energyanalytics-service" \
--data "url=${ENERGYANALYTICS_URL}"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
--data "name=flexforecasting-service" \
--data "url=${FLEXFORECASTING_URL}"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/" \
--data "name=ollama-service" \
--data "url=${OLLAMA_URL}" \
--data "read_timeout=300000" \
--data "write_timeout=300000" \
--data "connect_timeout=300000"


# create routes — strip_path=false so the full path reaches the upstream unchanged

# Prosumer
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
--data "paths[]=/Prosumer" \
--data "methods[]=GET" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
--data "paths[]=/Prosumer/assets" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
--data "paths[]=/Prosumer/assets/active" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
--data "paths[]=/Prosumer/assets/active/by-prosumers" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
--data-urlencode "paths[]=~/Prosumer/[0-9]+/assets" \
--data "methods[]=GET" \
--data "methods[]=POST" \
--data "methods[]=DELETE" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
--data-urlencode "paths[]=~/Prosumer/[0-9]+/.+" \
--data "methods[]=PUT" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/prosumer-service/routes" \
--data-urlencode "paths[]=~/Prosumer/[0-9]+" \
--data "methods[]=GET" \
--data "methods[]=DELETE" \
--data "strip_path=false"


# UtilityOperator
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
--data "paths[]=/UtilityOperator" \
--data "methods[]=GET" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
--data "paths[]=/UtilityOperator/gridcells" \
--data "methods[]=GET" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
--data-urlencode "paths[]=~/UtilityOperator/gridcells/.+" \
--data "methods[]=GET" \
--data "methods[]=PUT" \
--data "methods[]=DELETE" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
--data-urlencode "paths[]=~/UtilityOperator/[0-9]+/.+" \
--data "methods[]=PUT" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/utilityoperator-service/routes" \
--data-urlencode "paths[]=~/UtilityOperator/[0-9]+" \
--data "methods[]=GET" \
--data "methods[]=DELETE" \
--data "strip_path=false"


# AssetLink
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/assetlink-service/routes" \
--data "paths[]=/AssetLink" \
--data "methods[]=GET" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/assetlink-service/routes" \
--data "paths[]=/AssetLink/utilityoperator" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/assetlink-service/routes" \
--data "paths[]=/AssetLink/prosumerIds/by-operator" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/assetlink-service/routes" \
--data "paths[]=/AssetLink/prosumerIds/by-operators" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/assetlink-service/routes" \
--data-urlencode "paths[]=~/AssetLink/[0-9]+/[0-9]+" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/assetlink-service/routes" \
--data-urlencode "paths[]=~/AssetLink/[0-9]+" \
--data "methods[]=GET" \
--data "methods[]=DELETE" \
--data "strip_path=false"


# Telemetry
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
--data "paths[]=/Telemetry/Consume" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
--data "paths[]=/Telemetry/latest/bulk" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
--data-urlencode "paths[]=~/Telemetry/window/[^/]+/[0-9]+" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
--data-urlencode "paths[]=~/Telemetry/latest/[^/]+/[0-9]+" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
--data-urlencode "paths[]=~/Telemetry/latest/[0-9]+" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
--data "paths[]=/Telemetry" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/telemetry-service/routes" \
--data-urlencode "paths[]=~/Telemetry/[0-9]+" \
--data "methods[]=GET" \
--data "strip_path=false"


# FlexibilityEvent
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityevent-service/routes" \
--data "paths[]=/FlexibilityEvent/evaluate" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityevent-service/routes" \
--data "paths[]=/FlexibilityEvent/save" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityevent-service/routes" \
--data-urlencode "paths[]=~/FlexibilityEvent/logs/[0-9]+" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityevent-service/routes" \
--data "paths[]=/FlexibilityEvent/type" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityevent-service/routes" \
--data "paths[]=/FlexibilityEvent/asset" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityevent-service/routes" \
--data "paths[]=/FlexibilityEvent" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexibilityevent-service/routes" \
--data-urlencode "paths[]=~/FlexibilityEvent/[0-9]+" \
--data "methods[]=GET" \
--data "strip_path=false"


# GridBalancingRecommendation
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/gridbalancing-service/routes" \
--data "paths[]=/GridBalancingRecommendation/metrics" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/gridbalancing-service/routes" \
--data "paths[]=/GridBalancingRecommendation/save" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/gridbalancing-service/routes" \
--data-urlencode "paths[]=~/GridBalancingRecommendation/recommendations/[0-9]+" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/gridbalancing-service/routes" \
--data "paths[]=/GridBalancingRecommendation/source" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/gridbalancing-service/routes" \
--data "paths[]=/GridBalancingRecommendation" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/gridbalancing-service/routes" \
--data-urlencode "paths[]=~/GridBalancingRecommendation/[0-9]+" \
--data "methods[]=GET" \
--data "methods[]=PUT" \
--data "methods[]=DELETE" \
--data "strip_path=false"


# EnergyAnalytics
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/compute/generated-by-prosumer" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/compute/consumed-by-prosumer" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/compute/discharged-by-zone" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/compute/average-soc" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/persist/consume" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/persist/generate" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/persist/discharge" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/persist/average" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/discharged-by-zone" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/generated-by-prosumer" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/consumed-by-prosumer" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data "paths[]=/EnergyAnalytics/average-soc" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/energyanalytics-service/routes" \
--data-urlencode "paths[]=~/EnergyAnalytics/[^/]+/[0-9]+" \
--data "methods[]=GET" \
--data "strip_path=false"


# FlexibilityForecasting
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexforecasting-service/routes" \
--data "paths[]=/FlexibilityForecasting/evaluate-correlation" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexforecasting-service/routes" \
--data "paths[]=/FlexibilityForecasting/build-prompt" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexforecasting-service/routes" \
--data "paths[]=/FlexibilityForecasting/forecast" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexforecasting-service/routes" \
--data "paths[]=/FlexibilityForecasting/history" \
--data "methods[]=GET" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/flexforecasting-service/routes" \
--data-urlencode "paths[]=~/FlexibilityForecasting/history/[0-9]+" \
--data "methods[]=GET" \
--data "methods[]=DELETE" \
--data "strip_path=false"


# Ollama
curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/ollama-service/routes" \
--data "paths[]=/api/generate" \
--data "methods[]=POST" \
--data "strip_path=false"

curl -i -X POST \
--url "http://${KONG_SERVER_ADDRESS}:8001/services/ollama-service/routes" \
--data "paths[]=/api/tags" \
--data "methods[]=GET" \
--data "strip_path=false"


echo ""
echo "----Kong provisioning complete -----"
echo "Kong proxy available at: http://${KONG_SERVER_ADDRESS}:8000"
