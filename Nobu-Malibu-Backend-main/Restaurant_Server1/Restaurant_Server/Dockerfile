@'
# Paso 1: Compilar la aplicación usando Maven con Java 20
FROM maven:3.9.6-eclipse-temurin-20 AS build
COPY . .
RUN mvn clean package -DskipTests

# Paso 2: Ejecutar la aplicación usando Java 20 ligero
FROM eclipse-temurin:20-jre-alpine
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
'@ | Out-File -FilePath Dockerfile -Encoding utf8