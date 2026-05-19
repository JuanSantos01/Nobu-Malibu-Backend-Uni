Set-Content -Path Dockerfile -Value 'FROM maven:3.9.6-eclipse-temurin-20 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:20-jre-alpine
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]'