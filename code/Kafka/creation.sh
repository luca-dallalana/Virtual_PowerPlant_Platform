#!/bin/bash
echo "Starting..."
cd
sudo yum -y install java-17-amazon-corretto-devel.x86_64  
sudo wget https://dlcdn.apache.org/kafka/4.1.1/kafka_2.13-4.1.1.tgz
sudo tar -zxf kafka_2.13-4.1.1.tgz
cd kafka_2.13-4.1.1
KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
sudo bin/kafka-storage.sh format --standalone -t $KAFKA_CLUSTER_ID -c config/server.properties

TOKEN=`curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600"`
dnsname=`curl -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-hostname`

sudo sed -i "s/listeners=PLAINTEXT:\/\/:9092,CONTROLLER:\/\/:9093/listeners=PLAINTEXT:\/\/0.0.0.0:9092,CONTROLLER:\/\/:9093/g" config/server.properties
sudo sed -i "s/advertised.listeners=PLAINTEXT:\/\/localhost:9092,CONTROLLER:\/\/localhost:9093/advertised.listeners=PLAINTEXT:\/\/$dnsname:9092,CONTROLLER:\/\/localhost:9093/g" config/server.properties

sudo bin/kafka-server-start.sh config/server.properties&

# Create known application topics if missing.
sleep 5
topics=(
	"Energy-Discharged-By-Zone"
	"Generated-Energy-By-Prosumer"
	"Consumed-Energy-By-Prosumer"
	"Average-SoC"
	"GridBalancingRecommendation"
	"flexibility-offers"
)

for topic in "${topics[@]}"; do
	sudo bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
		--topic "$topic" --partitions 1 --replication-factor 1
done

echo "Finished."
