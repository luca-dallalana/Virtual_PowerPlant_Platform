terraform {
  required_version = ">= 1.0.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
}

resource "aws_instance" "exampleOllamaConfiguration" {
  ami                     = "ami-07ff62358b87c7116"
  instance_type           = "t3.large"
  count                   = 1
  vpc_security_group_ids  = [aws_security_group.instance.id]
  key_name                = "vockey"

  root_block_device {
    volume_size = 50
  }

  user_data = "${file("creation.sh")}"

  user_data_replace_on_change = true

  tags = {
    Name = "terraform-deploy-Ollama-VPPaaS"
  }
}

resource "aws_security_group" "instance" {
  name = var.security_group_name

  ingress {
    from_port        = 22
    to_port          = 22
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    from_port        = 11434
    to_port          = 11434
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

variable "security_group_name" {
  description = "The name of the security group"
  type        = string
  default     = "terraform-ollama-instance-vppas2026"
}

output "address" {
  value       = aws_instance.exampleOllamaConfiguration[*].public_dns
  description = "Ollama EC2 address (port 11434)"
}
