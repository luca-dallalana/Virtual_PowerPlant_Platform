#!/bin/bash

source ./access.sh
# access.sh defaults to Account 1


# ── ACCOUNT 2: destroy Quarkus microservices ──────────────────────────────────

use_account2

cd Quarkus-Terraform/telemetry
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/prosumer
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/utilityoperator
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/assetlink
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/flexibilityevent
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/gridbalancingrecommendation
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/energyanalytics
terraform destroy -auto-approve
cd ../..

cd Quarkus-Terraform/flexibilityforecasting
terraform destroy -auto-approve
cd ../..


# ── ACCOUNT 1: destroy infrastructure ────────────────────────────────────────

use_account1

cd RDS-Terraform
terraform destroy -auto-approve
cd ..

cd Camunda-Terraform
terraform destroy -auto-approve
cd ..

cd Kafka
terraform destroy -auto-approve
cd ..

cd OllamaTerraform
terraform destroy -auto-approve
cd ..

cd KongTerraform
terraform destroy -auto-approve
cd ..

cd KongaTerraform
terraform destroy -auto-approve
cd ..
