FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package

FROM openjdk:17
WORKDIR /app
COPY --from=build /app/target/backend-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
EXPOSE 3030
CMD ["java", "-jar", "app.jar"]
