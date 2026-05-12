# 1. The Foundation: A tiny Linux OS with Java 21 pre-installed
FROM eclipse-temurin:21-jdk-alpine

# 2. The Workspace: Create a folder named /app inside the container
WORKDIR /app

# 3. The Payload: Copy the JAR you just built into the container and rename it to app.jar
COPY target/discovery-server-0.0.1-SNAPSHOT.jar app.jar

# 4. The Port: Documenting the default port from your properties file
EXPOSE 9095

# 5. The Execution: The command to run when the container wakes up
ENTRYPOINT ["java", "-jar", "app.jar"]
