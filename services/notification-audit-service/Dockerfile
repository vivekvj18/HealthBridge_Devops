FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/notification-audit-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9099
ENTRYPOINT ["java", "-jar", "app.jar"]
