# [builder 스테이지]
# 의도: JDK 환경에서 전체 프로젝트를 빌드해서 gateway JAR 생성
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 의도: Gradle wrapper와 빌드 설정 파일만 먼저 복사
# 소스코드보다 변경 빈도가 낮아서 Docker 레이어 캐시 활용 가능
# 소스코드가 바뀌어도 의존성 다운로드 레이어는 재사용됨
COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .
COPY common/build.gradle common/build.gradle
COPY gateway/build.gradle gateway/build.gradle

# 의도: 소스코드 복사
COPY common/src common/src
COPY gateway/src gateway/src

# 의도: gateway shadowJar 빌드
RUN chmod +x gradlew && ./gradlew :gateway:shadowJar --no-daemon

# [runner 스테이지]
# 의도: JRE만 있는 경량 이미지에 JAR만 복사해서 실행
# JDK, Gradle, 소스코드 제외 → 이미지 크기 최소화
FROM eclipse-temurin:21-jre AS runner
WORKDIR /app

COPY --from=builder /app/gateway/build/libs/gateway-1.0.0.jar app.jar

# 의도: Asia/Seoul 타임존 설정
# -Duser.timezone으로 JVM 타임존 명시 (로컬 실행 시와 동일)
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]