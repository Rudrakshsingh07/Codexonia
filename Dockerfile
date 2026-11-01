# syntax=docker/dockerfile:1
# Codexonia web image: Spring Boot + static frontend in one jar.
# The runner shells out to the host Docker daemon through the mounted socket.

FROM maven:3.9-eclipse-temurin-26 AS build

WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:26-jre AS runtime

# Docker CLI only (no daemon). It talks to the socket mounted by Compose.
COPY --from=docker:29-cli /usr/local/bin/docker /usr/local/bin/docker

WORKDIR /app
COPY --from=build /build/target/codexonia-0.1.0.jar /app/codexonia.jar
RUN mkdir -p /var/lib/codexonia/jobs

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/codexonia.jar"]
