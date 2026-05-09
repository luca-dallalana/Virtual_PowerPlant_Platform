#!/bin/bash
echo "Starting..."

sudo yum install -y docker

sudo service docker start




echo "Finished."
sudo docker login -u "slguerreiro" -p "dfjfkd8Flnciw7"
sudo docker pull slguerreiro/gridbalancingrecommendation:1.0.0-SNAPSHOT
sudo docker run -d --name gridbalancingrecommendation -p 8084:8084 slguerreiro/gridbalancingrecommendation:1.0.0-SNAPSHOT
