#!/bin/bash
cd
sudo yum update -y
sudo curl -fsSL https://ollama.com/install.sh | sh
export HOME=$HOME:/usr/local/bin
sudo sed -i "s/\[Install\]/Environment=\"OLLAMA_HOST=0.0.0.0:11434\"\n\[Install\]/g" /etc/systemd/system/ollama.service
sudo systemctl daemon-reload
sudo systemctl enable ollama
sudo systemctl start ollama

until curl -sf http://localhost:11434 > /dev/null 2>&1; do sleep 2; done

ollama pull llama3.2:latest
