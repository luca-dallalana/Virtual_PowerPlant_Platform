#!/bin/bash
echo "Starting..."

sudo yum install -y docker

sudo service docker start




echo "Finished."
sudo docker login -u "slguerreiro" -p "dfjfkd8Flnciw7"
sudo docker pull slguerreiro/energyanalytics:1.0.0-SNAPSHOT
sudo docker run -d --name energyanalytics -p 8087:8087 slguerreiro/energyanalytics:1.0.0-SNAPSHOT
