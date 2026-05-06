#!/bin/bash
echo "Starting..."

sudo yum install -y docker

sudo service docker start




echo "Finished."
sudo docker login -u "slguerreiro" -p "dfjfkd8Flnciw7"
sudo docker pull slguerreiro/telemetry:1.0.0-SNAPSHOT
sudo docker run -d --name telemetry -p 8080:8080 slguerreiro/telemetry:1.0.0-SNAPSHOT
