FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/hospital-b-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9098
ENTRYPOINT ["java", "-jar", "app.jar"]
