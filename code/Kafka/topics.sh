#!/bin/bash
KAFKA_HOME=/home/ec2-user/kafka_2.13-4.1.1

echo "Waiting for Kafka directory to be created by user_data..."
until sudo test -d $KAFKA_HOME; do
  echo "Kafka directory not ready yet, retrying in 5s..."
  sleep 5
done

echo "Waiting for Kafka broker to be ready..."
until sudo $KAFKA_HOME/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null 2>&1; do
  echo "Kafka not ready yet, retrying in 5s..."
  sleep 5
done

echo "Kafka is ready. Creating topics..."

topics=(
  "Flexibility-Offers"
  "GridBalancingRecommendation"
  "Energy-Discharged-Zone"
  "Energy-Generated-Prosumer"
  "Energy-Consumed-Prosumer"
  "Average-SoC"
)

for topic in "${topics[@]}"; do
  sudo $KAFKA_HOME/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor 3
  echo "Created topic: $topic"
done

echo "All topics created."
