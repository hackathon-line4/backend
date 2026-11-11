# 배포 (EC2 1대 + Docker Compose)

프론트 연동용 개발 서버를 EC2 한 대에 띄우는 방법이다. 구성 파일은 `Dockerfile`, `deploy/docker-compose.yml`, `deploy/Caddyfile`.

```
앱(Spring Boot) ── Postgres 16 ── Caddy(80·443, HTTPS 자동 발급)
```

## 현재 제약

- **`local` 설정으로 띄운다.** `prod` 설정은 FCM `PushSender` 운영 구현이 없어 시작에 실패한다. `local`에서는 푸시가 실제로 나가지 않고 로그로만 남는다(SSE는 동작). 구현이 들어오면 `deploy/.env`의 `SPRING_PROFILES_ACTIVE=prod`로 바꾼다.
- **서버 1대 전제.** 로그인 실패 횟수·SSE 연결이 서버 메모리에 있어 여러 대로 늘리려면 Redis 같은 공유 저장소가 필요하다.
- **스키마는 `ddl-auto: update`.** 데이터가 쌓인 뒤 NOT NULL 컬럼이 추가되면 반영에 실패할 수 있다. 개발 서버 데이터는 초기화해도 되는 것으로 본다.

## 처음 띄우기

권장: Amazon Linux 2023, t4g.small(2GB), EBS gp3 20GB, 보안 그룹은 80·443 전체 허용 + 22는 내 IP만.

```bash
# 1. Docker·Compose·git 설치
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user   # 다시 접속하면 sudo 없이 docker 사용
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -sSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 2. 코드 받기
git clone https://github.com/hackathon-line4/backend.git && cd backend && git checkout dev

# 3. 서버 설정 (값 설명은 deploy/.env.example)
cp deploy/.env.example deploy/.env
vi deploy/.env   # DOMAIN, DB_PASSWORD, JWT_SECRET

# 4. 실행 (첫 빌드는 몇 분 걸린다)
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

- `DOMAIN`: IP만 있으면 `13-125-1-2.sslip.io`처럼 IP의 점을 하이픈으로 바꾼 sslip.io 주소를 쓰면 Caddy가 인증서를 받는다. 인증서 없이 HTTP로만 열려면 `:80`.
- 확인: `https://<DOMAIN>/swagger-ui/index.html`, `docker compose -f deploy/docker-compose.yml ps`

## 재배포 (dev가 바뀌었을 때)

```bash
cd backend && git pull
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
docker image prune -f   # 이전 이미지 정리(디스크 확보)
```

로그: `docker compose -f deploy/docker-compose.yml logs -f app`

## 서버 끄고 켜기

- EC2를 중지(stop)해도 EBS의 DB 데이터·인증서는 남는다. 다시 켜면 Docker와 컨테이너(`restart: unless-stopped`)가 자동으로 올라온다.
- 고정 IP(Elastic IP)가 없으면 켤 때마다 IP가 바뀌므로 sslip.io 주소와 앱 설정도 바뀐다.

## DB 백업

```bash
docker compose -f deploy/docker-compose.yml exec -T db pg_dump -U sonulim sonulim | gzip > backup-$(date +%F).sql.gz
# S3에 보관: aws s3 cp backup-YYYY-MM-DD.sql.gz s3://<버킷>/
```

## 정리 (발표 후)

1. 필요하면 위 방법으로 DB 백업
2. EC2 종료(terminate) — EBS도 함께 삭제되는지 확인
3. Elastic IP 해제(release) — 연결만 끊어 두면 계속 과금
4. 백업 S3 버킷·스냅샷 삭제
