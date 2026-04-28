# [builder 스테이지]
# 의도: JDK 환경에서 전체 프로젝트를 빌드해서 aipipeline JAR 생성
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 의도: Gradle wrapper와 빌드 설정 파일만 먼저 복사 → 의존성 레이어 캐시 활용
COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .
COPY aipipeline/build.gradle aipipeline/build.gradle

# 의도: 소스코드 복사
COPY aipipeline/src aipipeline/src

# 의도: Spring Boot executable jar 빌드 (-x test로 테스트 제외)
RUN chmod +x gradlew && ./gradlew :aipipeline:bootJar -x test --no-daemon

# [runner 스테이지]
FROM eclipse-temurin:21-jre AS runner
WORKDIR /app

COPY --from=builder /app/aipipeline/build/libs/aipipeline-1.0.0.jar app.jar

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]