#!/bin/bash
echo "Installing Docker..."
sudo yum install -y docker
sudo service docker start

echo "Pulling Ollama image..."
sudo docker pull ollama/ollama:latest

echo "Starting Ollama server..."
sudo docker run -d --name ollama \
  -p 11434:11434 \
  -v ollama-models:/root/.ollama \
  ollama/ollama:latest

echo "Waiting for Ollama to start..."
sleep 10

echo "Pulling llama3.2 model..."
sudo docker exec ollama ollama pull llama3.2:latest

echo "Deploying FlexibilityForecasting microservice..."
sudo docker login -u "slguerreiro" -p "dfjfkd8Flnciw7"
sudo docker pull slguerreiro/flexibilityforecasting:1.0.0-SNAPSHOT
sudo docker run -d --name flexibilityforecasting \
  -p 8087:8087 \
  --link ollama:ollama \
  -e QUARKUS_REST_CLIENT_OLLAMA_API_URL=http://ollama:11434 \
  slguerreiro/flexibilityforecasting:1.0.0-SNAPSHOT

echo "Deployment complete!"
