#!/bin/bash
echo "Starting..."

sudo yum install -y docker

sudo service docker start

echo "Finished."
