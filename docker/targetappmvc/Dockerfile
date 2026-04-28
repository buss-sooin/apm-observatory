# [builder 스테이지]
# 의도: JDK 환경에서 targetappmvc JAR + agent shadowJar 동시 빌드
# agent는 -javaagent로 targetappmvc JVM에 붙어서 실행되므로 같은 컨테이너에 있어야 함
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 의도: Gradle wrapper와 빌드 설정 파일만 먼저 복사 → 의존성 레이어 캐시 활용
COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .
COPY common/build.gradle common/build.gradle
COPY agent/build.gradle agent/build.gradle
COPY targetappmvc/build.gradle targetappmvc/build.gradle

# 의도: 소스코드 복사
COPY common/src common/src
COPY agent/src agent/src
COPY targetappmvc/src targetappmvc/src

# 의도: targetappmvc bootJar + agent shadowJar 동시 빌드
RUN chmod +x gradlew && \
    ./gradlew :targetappmvc:bootJar :agent:shadowJar -x test --no-daemon

# [runner 스테이지]
FROM eclipse-temurin:21-jre AS runner
WORKDIR /app

# 의도: targetappmvc JAR + agent JAR 둘 다 복사
COPY --from=builder /app/targetappmvc/build/libs/targetappmvc-1.0.0.jar app.jar
COPY --from=builder /app/agent/build/libs/agent-1.0.0.jar agent.jar

# 의도: -javaagent로 agent를 targetappmvc JVM에 붙여서 실행
# 로컬 실행 시와 동일한 구조
# 변경 후
# --add-opens java.base/java.lang=ALL-UNNAMED 추가 이유:
#   Java 9+ 모듈 시스템이 ClassLoader.defineClass()를 protected로 막아둠
#   이 옵션이 없으면 setAccessible(true) 호출 시 InaccessibleObjectException 발생
#   와탭랩스 에이전트 설치 가이드에서 이 옵션을 쓰는 이유와 동일
ENTRYPOINT ["java", \
  "-Duser.timezone=Asia/Seoul", \
  "-Dapm.app.name=targetappmvc", \
  "-javaagent:/app/agent.jar", \
  "-jar", "/app/app.jar"]