# Paso 1: Compilar la aplicación con Maven oficial y Java
FROM maven:3.8.8-openjdk-18 AS build
COPY . .
RUN mvn clean package -DskipTests

# Paso 2: Ejecutar la aplicación con Java estándar
FROM openjdk:20-slim
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
