output "public_ip" {
  description = "서버 퍼블릭 IP (중지 후 다시 켜면 바뀐다)"
  value       = aws_instance.server.public_ip
}

output "ssh_command" {
  description = "SSH 접속 명령 (공개 키와 짝인 개인 키 사용)"
  value       = "ssh ec2-user@${aws_instance.server.public_ip}"
}

output "api_url" {
  description = "프론트가 붙을 API 주소"
  value       = "http://${aws_instance.server.public_ip}:${var.api_port}"
}
