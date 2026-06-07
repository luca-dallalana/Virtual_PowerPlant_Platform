#!/bin/bash
echo "Starting..."
cd /home/ec2-user
sudo yum -y install java-17-amazon-corretto-devel.x86_64
sudo wget https://dlcdn.apache.org/kafka/4.1.1/kafka_2.13-4.1.1.tgz
sudo tar -zxf kafka_2.13-4.1.1.tgz
sudo chown -R ec2-user:ec2-user /home/ec2-user/kafka_2.13-4.1.1
cd kafka_2.13-4.1.1

TOKEN=`curl -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600"`
dnsname=`curl -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-hostname`

echo "Waiting for cluster_hosts to be written by Terraform..."
until [ -s /home/ec2-user/cluster_hosts ] && [ -s /home/ec2-user/cluster_hosts_old_format ]; do
  echo "cluster_hosts not ready yet, retrying in 2s..."
  sleep 2
done

clusterlist=$(cat /home/ec2-user/cluster_hosts)
oldclusterlist=$(cat /home/ec2-user/cluster_hosts_old_format)

sudo sed -i "s/node.id=1/node.id=${idBroker + 1}/g" config/server.properties
sudo sed -i "s/listeners=PLAINTEXT:\/\/:9092,CONTROLLER:\/\/:9093/listeners=PLAINTEXT:\/\/0.0.0.0:9092,CONTROLLER:\/\/0.0.0.0:9093/g" config/server.properties
sudo sed -i "s|^advertised.listeners=.*|advertised.listeners=PLAINTEXT://$dnsname:9092|" config/server.properties
sudo sed -i "s/controller.quorum.bootstrap.servers=localhost:9093/controller.quorum.bootstrap.servers=$clusterlist/g" config/server.properties

KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2M
sudo bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c config/server.properties --initial-controllers $oldclusterlist

echo "sudo bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c config/server.properties --initial-controllers $oldclusterlist"

sudo bin/kafka-server-start.sh config/server.properties &

echo "Finished."
