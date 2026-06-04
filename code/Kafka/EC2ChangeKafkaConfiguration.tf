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

variable "nBroker" {
  description = "Number of Kafka brokers in the cluster"
  type        = number
  default     = 3
}

resource "aws_instance" "exampleKafkaConfiguration" {
  ami                    = "ami-07ff62358b87c7116"
  instance_type          = "t3.small"
  count                  = var.nBroker
  vpc_security_group_ids = [aws_security_group.instance.id]
  key_name               = "vockey"

  user_data = base64encode(templatefile("creation.sh", {
    idBroker     = count.index
    totalBrokers = var.nBroker
  }))

  user_data_replace_on_change = true

  tags = {
    Name = "terraform-kafka.${count.index}"
  }
}

output "publicdnslist" {
  value = formatlist("%v", aws_instance.exampleKafkaConfiguration.*.public_dns)
}

locals {
  replica_directorys = [
    "r1eMpKZRROex80kgn4_2-g",
    "bWIe6tPFS3mKvq-W-_kgtQ",
    "Z57HkCAASdifFa-QWCdGWA"
  ]
  quorum_voters = join(",", [
    for i, instance in aws_instance.exampleKafkaConfiguration :
    "${i + 1}@${instance.public_dns}:9093:${local.replica_directorys[i]}"
  ])
}

resource "null_resource" "update_dns" {
  count = var.nBroker

  connection {
    type        = "ssh"
    user        = "ec2-user"
    private_key = file("key.pem")
    host        = aws_instance.exampleKafkaConfiguration[count.index].public_ip
  }

  provisioner "remote-exec" {
    inline = [
      "echo -n '${join(":9093,", aws_instance.exampleKafkaConfiguration.*.public_dns)}:9093' > cluster_hosts",
      "echo -n '${local.quorum_voters}' > cluster_hosts_old_format"
    ]
  }
}

resource "null_resource" "create_topics" {
  depends_on = [null_resource.update_dns]

  connection {
    type        = "ssh"
    user        = "ec2-user"
    private_key = file("key.pem")
    host        = aws_instance.exampleKafkaConfiguration[0].public_ip
  }

  provisioner "file" {
    source      = "topics.sh"
    destination = "/home/ec2-user/topics.sh"
  }

  provisioner "remote-exec" {
    inline = [
      "chmod +x /home/ec2-user/topics.sh",
      "/home/ec2-user/topics.sh"
    ]
  }
}

resource "aws_security_group" "instance" {
  name = var.security_group_name

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 9092
    to_port     = 9092
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 9093
    to_port     = 9093
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
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
  default     = "terraform-example-instance2026"
}
