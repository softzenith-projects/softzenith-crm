# syntax=docker/dockerfile:1
# AimDesk API image. Built by deploy/docker-compose.yml; tests run in CI / locally, not here.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
# The Maven cache survives between builds, so later deploys only download what changed.
RUN --mount=type=cache,target=/root/.m2 \
    sh mvnw -B -q -DskipTests package && cp target/crm-*.jar /src/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 crm && mkdir -p /app/logs && chown crm /app/logs
COPY --from=build /src/app.jar app.jar
USER crm
EXPOSE 8081
# Heap and GC come from JAVA_TOOL_OPTIONS (deploy/docker-compose.yml), sized for the server.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
