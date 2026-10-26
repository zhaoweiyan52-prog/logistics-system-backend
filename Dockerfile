FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/logistics-backend-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --chown=10001:10001 docker/entrypoint.sh /app/entrypoint.sh
ENV SPRING_PROFILES_ACTIVE=cloud \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["/bin/sh", "/app/entrypoint.sh"]
