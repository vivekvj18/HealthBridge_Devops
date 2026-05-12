FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/hie-fhir-exchange-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9096
ENTRYPOINT ["java", "-jar", "app.jar"]
