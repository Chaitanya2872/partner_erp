# Multi-stage build for the Partner ERP backend (Java 17). Hardened further in Phase 15.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /build/target/partner-erp-server-*.jar app.jar
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prd \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
# Container health check is defined in prd/docker-compose.yml (Phase 15) against /actuator/health/liveness.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
