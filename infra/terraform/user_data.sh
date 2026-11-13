#!/bin/bash
# EC2가 처음 켜질 때 한 번 실행된다(root). 진행 로그: /var/log/cloud-init-output.log
set -euxo pipefail

# Docker
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

# Docker Compose 플러그인 — AL2023 docker 패키지에 들어 있지 않다. 이미지는 내 PC에서 빌드해 올리므로 buildx는 두지 않는다.
COMPOSE_VERSION=v2.29.7
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-aarch64" \
	-o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 스왑 2GB — 메모리 2GB에서 JVM과 Postgres가 함께 돌 때 OOM을 막는다.
if [ ! -f /swapfile ]; then
	dd if=/dev/zero of=/swapfile bs=1M count=2048
	chmod 600 /swapfile
	mkswap /swapfile
	swapon /swapfile
	echo '/swapfile none swap defaults 0 0' >> /etc/fstab
fi

# 앱 파일(docker-compose.yml, .env, secret/)을 둘 폴더. secret/은 목록은 숨기고(711) 통과만 허용한다 —
# 앱 컨테이너는 uid 1001로 돌아서, 키 파일은 올린 뒤 1001 소유로 바꿔야 읽을 수 있다(README 참고).
install -d -o ec2-user -g ec2-user -m 755 /home/ec2-user/sonulim
install -d -o ec2-user -g ec2-user -m 711 /home/ec2-user/sonulim/secret
