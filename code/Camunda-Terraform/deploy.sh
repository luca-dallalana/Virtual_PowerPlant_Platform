#!/bin/bash
echo "Starting..."

sudo yum -y install java-21-amazon-corretto-devel.x86_64 

wget https://downloads.camunda.cloud/release/camunda/c8run/8.8.9/camunda8-run-8.8.9-linux-x86_64.tar.gz

sudo tar xvf camunda8-run-8.8.9-linux-x86_64.tar.gz

sudo rm camunda8-run-8.8.9-linux-x86_64.tar.gz

sudo chmod -R 777 c8run

sudo runuser -l ec2-user -c 'cd /c8run && ./start.sh'

echo "Finished."
