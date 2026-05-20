# Paso 1: Compilar la aplicación usando Maven con Java 17
FROM maven:3.9.6-amazoncorretto-17 AS build
COPY . .
RUN mvn clean package -DskipTests

# Paso 2: Ejecutar la aplicación usando Java 17 ligero
FROM amazoncorretto:17-alpine
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
