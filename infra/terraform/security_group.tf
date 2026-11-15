# 기본 VPC를 그대로 쓴다 — 서버 1대라 VPC·서브넷을 따로 만들지 않는다.
data "aws_vpc" "default" {
  default = true
}

# SSH는 내 IP만, API 포트만 외부에 연다. Postgres(5433)는 열지 않는다 — 앱 컨테이너만 compose 내부망으로 접속한다.
resource "aws_security_group" "server" {
  name        = "${var.project}-server"
  description = "sonulim dev server: SSH from my IP, API public"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  security_group_id = aws_security_group.server.id
  description       = "SSH from my IP"
  cidr_ipv4         = var.ssh_cidr
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
}

resource "aws_vpc_security_group_ingress_rule" "api" {
  security_group_id = aws_security_group.server.id
  description       = "Backend API"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = var.api_port
  to_port           = var.api_port
}

# Docker 이미지·패키지 설치, FCM 발송에 바깥 통신이 필요하다.
resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.server.id
  description       = "All outbound"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
