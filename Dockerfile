FROM openjdk:17
WORKDIR /app
COPY target/backend-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
EXPOSE 3030
CMD ["java", "-jar", "app.jar"]
