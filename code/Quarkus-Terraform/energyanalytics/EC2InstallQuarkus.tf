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
  region      = "us-east-1"
}

resource "aws_instance" "exampleDeployQuarkus" {
    # Amazon Linux ARM AMI built by Amazon Web Services - FOR DOCKER image COMPATIBILITY if compiled previously on ARM
    # adapt accordingly the architecture where you compiled it
  ami                     = "ami-0bb7267a511c0a8e8"
  instance_type           = "t4g.small"
  vpc_security_group_ids  = [aws_security_group.instance.id]
  key_name                = "vockey"

  user_data = "${file("quarkus.sh")}"

  user_data_replace_on_change = true

  tags = {
    Name = "terraform-deploy-QuarkusProject-energyanalytics"
  }
}

resource "aws_security_group" "instance" {
  name = var.security_group_name
  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
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
  default     = "terraform-Quarkus-instance-energyanalytics2026"
}
