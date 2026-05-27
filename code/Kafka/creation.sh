#!/bin/bash
echo "Starting..."
cd
sudo yum -y install java-17-amazon-corretto-devel.x86_64
sudo wget https://dlcdn.apache.org/kafka/4.1.1/kafka_2.13-4.1.1.tgz
sudo tar -zxf kafka_2.13-4.1.1.tgz
cd kafka_2.13-4.1.1

TOKEN=`curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600"`
dnsname=`curl -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-hostname`
clusterlist=$(cat /home/ec2-user/cluster_hosts)
oldclusterlist=$(cat /home/ec2-user/cluster_hosts_old_format)

sudo sed -i "s/node.id=1/node.id=${idBroker + 1}/g" config/server.properties
sudo sed -i "s/listeners=PLAINTEXT:\/\/:9092,CONTROLLER:\/\/:9093/listeners=PLAINTEXT:\/\/0.0.0.0:9092,CONTROLLER:\/\/0.0.0.0:9093/g" config/server.properties
sudo sed -i "s/advertised.listeners=PLAINTEXT:\/\/localhost:9092,CONTROLLER:\/\/localhost:9093/advertised.listeners=PLAINTEXT:\/\/$dnsname:9092/g" config/server.properties
sudo sed -i "s/controller.quorum.bootstrap.servers=localhost:9093/controller.quorum.bootstrap.servers=$clusterlist/g" config/server.properties

KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2M
sudo bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c config/server.properties --initial-controllers $oldclusterlist

echo "sudo bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c config/server.properties --initial-controllers $oldclusterlist"

sudo bin/kafka-server-start.sh config/server.properties &

%{ if idBroker == 0 }
sleep 15
topics=(
  "Flexibility-Offers"
  "GridBalancingRecommendation"
  "Energy-Discharged-Zone"
  "Energy-Generated-Prosumer"
  "Energy-Consumed-Prosumer"
  "Average-SoC"
)
for topic in "$${topics[@]}"; do
  sudo bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor 3
done
%{ endif }

echo "Finished."
