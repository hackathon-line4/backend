# Amazon Linux 2023 arm64 최신 AMI. 나중에 AMI가 새로 나와도 서버를 다시 만들지 않도록 ami 변경은 무시한다.
data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

# 개인 키는 내 PC에만 두고 공개 키만 등록한다.
resource "aws_key_pair" "server" {
  key_name   = "${var.project}-server"
  public_key = file(pathexpand(var.public_key_path))
}

# 고정 IP(Elastic IP)는 쓰지 않는다 — 중지 후 다시 켜면 퍼블릭 IP가 바뀐다(terraform output으로 다시 확인).
resource "aws_instance" "server" {
  ami                         = data.aws_ssm_parameter.al2023_arm64.value
  instance_type               = var.instance_type
  key_name                    = aws_key_pair.server.key_name
  vpc_security_group_ids      = [aws_security_group.server.id]
  associate_public_ip_address = true

  # 처음 켜질 때 Docker·Compose 설치와 스왑 설정(user_data.sh).
  user_data = file("${path.module}/user_data.sh")

  # 메타데이터는 IMDSv2(토큰)만 허용한다.
  metadata_options {
    http_tokens = "required"
  }

  # DB 데이터도 이 볼륨에 쌓인다(Docker 볼륨). 서버를 지우면 함께 지워진다.
  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = "${var.project}-server"
  }

  lifecycle {
    ignore_changes = [ami]
  }
}
