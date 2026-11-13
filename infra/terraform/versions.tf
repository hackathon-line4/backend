# 손울림 개발 서버(EC2 1대) Terraform 구성. 상태 파일은 로컬에만 둔다(.gitignore) — 1명이 관리하는 해커톤용 서버라
# S3 원격 상태·잠금은 두지 않는다.

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}
