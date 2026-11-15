# 개발 서버 (Terraform · EC2 1대)

프론트 연동·발표용 백엔드 서버. EC2 1대(t4g.small, arm64)에 최상위 `docker-compose.yml`로 앱 + Postgres를 띄운다.

- 만드는 것: 보안 그룹(SSH는 내 IP만, API 8080 공개), 키 페어, EC2 + 루트 EBS 20GB(gp3, 암호화)
- 만들지 않는 것: 도메인·HTTPS, 고정 IP, RDS, ECR·CD
- 비밀 값(`JWT_SECRET`, DB 비밀번호, FCM 서비스 계정 키)은 Terraform·레포에 넣지 않고 서버에 직접 올린다
- 서버에서 소스를 빌드하지 않는다 — 메모리 2GB에서 Gradle 빌드는 느리고 무거워서, 이미지는 내 PC(Apple Silicon, arm64)에서 빌드해 보낸다
- 주소는 DuckDNS 서브도메인(`http://<서브도메인>.duckdns.org:8080`) — 고정 IP가 없어 다시 켤 때마다 IP가 바뀌므로, 서버가 부팅할 때마다 DuckDNS에 현재 IP를 등록한다. 실제 주소는 레포가 아니라 팀 채널에 공유한다

**비용(서울, 대략):** EC2 t4g.small 시간당 약 $0.02 + 퍼블릭 IPv4 시간당 $0.005 + EBS 20GB 월 약 $2. 켜 두면 월 $20 안팎.
중지하면 EC2 요금은 멈추고 EBS 요금만 남는다. 다 쓰면 `terraform destroy`.

## 준비 (처음 한 번)

- AWS CLI 로그인: `aws configure` 또는 `AWS_PROFILE` 지정 (`aws sts get-caller-identity`로 확인)
- SSH 키: `~/.ssh/id_ed25519.pub`가 없으면 `ssh-keygen -t ed25519`
- 값 파일:

  ```bash
  cd infra/terraform
  cp terraform.tfvars.example terraform.tfvars
  # ssh_cidr에 내 IP/32 입력: curl -s https://checkip.amazonaws.com
  ```

## 1. 서버 만들기

```bash
cd infra/terraform
terraform init
terraform plan     # 만들 리소스 확인
terraform apply    # 이때부터 요금 발생
terraform output   # public_ip, ssh_command, api_url
```

서버가 켜진 뒤 Docker 설치까지 2~3분 걸린다. 끝났는지 확인:

```bash
IP=$(terraform output -raw public_ip)
ssh ec2-user@$IP 'docker compose version'
```

## 2. 앱 올리기

레포 최상위에서 실행한다.

```bash
IP=$(terraform -chdir=infra/terraform output -raw public_ip)

# 이미지 빌드 후 서버로 전송 (Postgres 이미지는 서버가 직접 받는다)
docker compose build app
docker save sonulim-backend:local | gzip | ssh ec2-user@$IP 'gunzip | docker load'

# compose 파일과 FCM 키
scp docker-compose.yml ec2-user@$IP:sonulim/
scp secret/<키 파일>.json ec2-user@$IP:sonulim/secret/
# 앱 컨테이너는 uid 1001로 돈다 — 키를 1001 소유로 바꿔야 읽을 수 있다
ssh ec2-user@$IP 'sudo chown 1001:1001 sonulim/secret/*.json && sudo chmod 400 sonulim/secret/*.json'
```

DuckDNS 토큰 (처음 한 번). 토큰은 duckdns.org 로그인 화면에 있다. 레포·카톡·노션에 올리지 않는다.

```bash
read -rs DUCKDNS_TOKEN   # 토큰 붙여넣고 엔터 (화면·셸 기록에 남지 않음)
ssh ec2-user@$IP 'sudo tee /etc/duckdns.env >/dev/null && sudo chmod 600 /etc/duckdns.env' \
	<<< "DUCKDNS_DOMAIN=<서브도메인>
DUCKDNS_TOKEN=$DUCKDNS_TOKEN"
unset DUCKDNS_TOKEN
ssh ec2-user@$IP 'sudo systemctl start duckdns && journalctl -u duckdns -n 3 --no-pager'   # "duckdns <서브도메인>: OK"
```

이후로는 서버가 켜질 때마다 자동으로 등록된다.

서버의 `.env` (처음 한 번). DB 비밀번호는 DB가 처음 만들어질 때만 반영되니 첫 실행 전에 정한다.

```bash
ssh ec2-user@$IP
cd sonulim
cat > .env <<EOF
JWT_SECRET=$(openssl rand -base64 48)
DB_PASSWORD=$(openssl rand -hex 16)
FIREBASE_KEY_FILE=<키 파일>.json
EOF
chmod 600 .env
docker compose up -d --no-build
```

확인: `curl -s -o /dev/null -w '%{http_code}\n' http://<서브도메인>.duckdns.org:8080/v3/api-docs` → `200` (DNS 반영에 1분 정도 걸릴 수 있다. 그동안은 `http://$IP:8080`)

- 앱은 `docker-compose.yml`의 `local` 설정 그대로 돈다. `JWT_SECRET`을 꼭 지정한다 — 비우면 레포에 공개된 개발용 키를 쓴다
- Android 9 이상은 기본적으로 `http://`를 막는다. 프론트는 개발 빌드에서 cleartext 허용(`usesCleartextTraffic` 또는 network security config)이 필요하다

## 3. 다시 배포 (코드 바뀌었을 때)

```bash
docker compose build app
docker save sonulim-backend:local | gzip | ssh ec2-user@$IP 'gunzip | docker load'
ssh ec2-user@$IP 'cd sonulim && docker compose up -d --no-build'
```

`docker-compose.yml`이 바뀌었으면 `scp`로 다시 올린다. DB 데이터는 유지된다.

## 4. 끄기 · 켜기 · 지우기

```bash
ID=$(aws ec2 describe-instances --filters "Name=tag:Name,Values=sonulim-server" "Name=instance-state-name,Values=running,stopped" \
	--query 'Reservations[0].Instances[0].InstanceId' --output text)
aws ec2 stop-instances --instance-ids $ID    # EC2 요금 멈춤 (EBS는 계속)
aws ec2 start-instances --instance-ids $ID   # 퍼블릭 IP가 바뀐다 → DuckDNS에 자동 등록
```

컨테이너는 `restart: unless-stopped`라 서버가 다시 켜지면 자동으로 올라오고, DuckDNS도 부팅 때 새 IP로 갱신된다 — 프론트는 `http://<서브도메인>.duckdns.org:8080` 그대로 쓴다.
SSH용 새 IP는 `terraform refresh` 후 `terraform output`으로 확인한다(ssh는 `ssh ec2-user@<서브도메인>.duckdns.org`도 된다).

```bash
cd infra/terraform
terraform destroy   # 서버·디스크·보안 그룹·키 페어 모두 삭제 (DB 데이터도 삭제)
```

## 로그

```bash
ssh ec2-user@$IP 'cd sonulim && docker compose logs -f app'
ssh ec2-user@$IP 'sudo tail -n 50 /var/log/cloud-init-output.log'   # 첫 설치 로그
```
