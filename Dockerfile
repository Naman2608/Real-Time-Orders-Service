# Stage 1 — build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2 — runtime (slim JRE)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/orders-realtime-1.0.0.jar app.jar
EXPOSE 8080
# 'docker' profile connects to the postgres container defined in docker-compose.yml
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
