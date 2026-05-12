FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/hospital-a-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9097
ENTRYPOINT ["java", "-jar", "app.jar"]
