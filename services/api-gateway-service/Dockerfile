# 1. Base image with Java 21
FROM eclipse-temurin:21-jdk-alpine

# 2. Workspace directory
WORKDIR /app

# 3. Copy the JAR file we just built
COPY target/api-gateway-service-0.0.1-SNAPSHOT.jar app.jar

# 4. Document the port (Gateway default is 9091)
EXPOSE 9091

# 5. Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

