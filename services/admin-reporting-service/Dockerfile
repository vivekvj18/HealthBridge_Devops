FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/admin-reporting-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
