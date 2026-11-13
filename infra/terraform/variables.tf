variable "project" {
  description = "리소스 이름·태그 앞에 붙는 이름"
  type        = string
  default     = "sonulim"
}

variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

# arm64(Graviton) 타입만 쓴다 — AMI도 arm64로 고르고, Apple Silicon 맥에서 빌드한 이미지를 그대로 올린다.
# 2GB면 앱(JVM) + Postgres가 함께 돈다(Dockerfile의 MaxRAMPercentage=75 기준).
variable "instance_type" {
  description = "EC2 인스턴스 타입 (arm64 계열: t4g.*)"
  type        = string
  default     = "t4g.small"
}

variable "root_volume_size" {
  description = "루트 EBS(gp3) 크기(GB). OS + Docker 이미지 + DB 데이터"
  type        = number
  default     = 20
}

variable "ssh_cidr" {
  description = "SSH(22)를 허용할 내 IP. 예: 203.0.113.10/32 (curl -s https://checkip.amazonaws.com 으로 확인)"
  type        = string
}

variable "public_key_path" {
  description = "EC2 키 페어로 등록할 SSH 공개 키 경로. 개인 키는 내 PC에만 둔다"
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "api_port" {
  description = "외부에 여는 백엔드 API 포트 (docker-compose.yml의 APP_PORT)"
  type        = number
  default     = 8080
}
