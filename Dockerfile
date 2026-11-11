# 손울림 백엔드 이미지. 1단계에서 jar를 빌드하고 2단계에는 JRE와 jar만 담는다.
# eclipse-temurin은 amd64·arm64 둘 다 제공해 EC2 t4g(ARM)에서도 그대로 빌드·실행된다.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 다운로드를 소스와 분리해 소스만 바뀌면 이 층을 재사용한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# 체크아웃 환경에 따라 실행 권한이 빠질 수 있어 한 번 더 지정한다.
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# 테스트는 CI에서 돌리므로 이미지 빌드에서는 실행 jar만 만든다.
RUN ./gradlew bootJar --no-daemon -q \
	&& cp "$(ls build/libs/*.jar | grep -v plain)" app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --uid 1001 --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app

EXPOSE 8080
# 컨테이너 메모리의 75%까지만 힙으로 쓴다(2GB 서버에서 Postgres·Caddy와 함께 실행).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
