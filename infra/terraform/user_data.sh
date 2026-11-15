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

# DuckDNS — 중지 후 다시 켜면 퍼블릭 IP가 바뀌므로 부팅할 때마다 현재 IP를 등록한다.
# 도메인·토큰은 /etc/duckdns.env에 직접 올린다(README). 파일이 없으면 이 서비스는 건너뛴다.
cat > /usr/local/bin/duckdns-update <<'EOF'
#!/bin/bash
# ip를 비워 보내면 DuckDNS가 요청한 서버의 IP로 등록한다. 토큰이 로그에 남지 않도록 응답만 출력한다.
set -euo pipefail
result=$(curl -fsS --retry 5 --retry-delay 3 --retry-all-errors \
	"https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=")
echo "duckdns ${DUCKDNS_DOMAIN}: ${result}"
[ "${result}" = "OK" ]
EOF
chmod 755 /usr/local/bin/duckdns-update

cat > /etc/systemd/system/duckdns.service <<'EOF'
[Unit]
Description=Register this server's public IP to DuckDNS
Wants=network-online.target
After=network-online.target
ConditionPathExists=/etc/duckdns.env

[Service]
Type=oneshot
EnvironmentFile=/etc/duckdns.env
ExecStart=/usr/local/bin/duckdns-update

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable duckdns.service

# 앱 파일(docker-compose.yml, .env, secret/)을 둘 폴더. secret/은 목록은 숨기고(711) 통과만 허용한다 —
# 앱 컨테이너는 uid 1001로 돌아서, 키 파일은 올린 뒤 1001 소유로 바꿔야 읽을 수 있다(README 참고).
install -d -o ec2-user -g ec2-user -m 755 /home/ec2-user/sonulim
install -d -o ec2-user -g ec2-user -m 711 /home/ec2-user/sonulim/secret
