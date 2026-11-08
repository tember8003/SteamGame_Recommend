# syntax=docker/dockerfile:1

# 빌드 스테이지: gradle wrapper 로 bootJar 생성함
# BUILDPLATFORM 으로 러너의 네이티브 아키텍처에서 빌드해 arm64 크로스빌드 때 gradle 컴파일이 에뮬레이션을 안 탄다, jar 는 아키텍처 중립이라 런타임 단계에서 그대로 얹는다
FROM --platform=$BUILDPLATFORM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
# Gradle home 을 캐시 마운트해 로컬 반복 빌드 시 의존성 재다운로드를 줄임
# 윈도우 체크아웃 시 gradlew 가 CRLF 면 컨테이너 셸 셔뱅이 깨지므로 LF 로 정규화함
RUN --mount=type=cache,target=/root/.gradle \
    sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon clean bootJar

# 런타임 스테이지: JRE 에 jar 만 복사함
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/recommend-0.0.1-SNAPSHOT.jar app.jar
# COPY 뒤에 둬서 커밋만 바뀌어도 위 레이어 캐시를 덜 깨게 함, 값은 배포 워크플로가 github.sha 로 채움
ARG APP_REVISION=unknown
ENV APP_REVISION=$APP_REVISION
EXPOSE 8888
# exec 형식이라 JVM 이 PID 1 로 SIGTERM 을 직접 받아 graceful shutdown 함
# 힙 제한은 JAVA_TOOL_OPTIONS 로, 배치 인자는 뒤에 붙여 program args 로 전달함
ENTRYPOINT ["java", "-jar", "app.jar"]
