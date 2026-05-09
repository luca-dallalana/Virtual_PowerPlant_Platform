#!/bin/bash
echo "Starting..."

sudo yum install -y docker

sudo service docker start




echo "Finished."
sudo docker login -u "slguerreiro" -p "dfjfkd8Flnciw7"
sudo docker pull slguerreiro/flexibilityevent:1.0.0-SNAPSHOT
sudo docker run -d --name flexibilityevent -p 8085:8085 slguerreiro/flexibilityevent:1.0.0-SNAPSHOT
