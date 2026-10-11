# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S gateway && adduser -S -G gateway gateway
WORKDIR /app
COPY --from=build --chown=gateway:gateway /workspace/target/billgen-gateway.jar /app/billgen-gateway.jar
USER gateway
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/billgen-gateway.jar"]
