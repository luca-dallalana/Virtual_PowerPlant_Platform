#!/bin/bash


source ./access.sh

#Terraform - Quarkus telemetry
cd Quarkus-Terraform/telemetry
terraform destroy -auto-approve
cd ../..

# #Terraform - Quarkus prosumer
cd Quarkus-Terraform/prosumer
terraform destroy -auto-approve
cd ../..

# #Terraform - Quarkus utilityoperator
cd Quarkus-Terraform/utilityoperator
terraform destroy -auto-approve
cd ../..

# #Terraform - Quarkus assetlink
cd Quarkus-Terraform/assetlink
terraform destroy -auto-approve
cd ../..

# #Terraform - RDS
cd RDS-Terraform
terraform destroy -auto-approve
cd ..

# #Terraform - Camunda
cd Camunda-Terraform
terraform destroy -auto-approve
cd ..

# # #Terraform - Kafka
cd Kafka
terraform destroy -auto-approve
cd ..

# # #Terraform - Kong
cd KongTerraform
terraform destroy -auto-approve
cd ..

# # #Terraform - Konga
cd KongaTerraform
terraform destroy -auto-approve
cd ..