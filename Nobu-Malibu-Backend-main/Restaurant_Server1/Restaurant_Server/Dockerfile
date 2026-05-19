# Paso 1: Compilar la aplicación usando Maven
FROM maven:3.8.8-jakartaee-10 AS build
COPY . .
RUN mvn clean package -DskipTests

# Paso 2: Ejecutar la aplicación usando Java 20
FROM openjdk:20-ea-slim-cdr
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]