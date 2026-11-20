# Etapa 1: compilar con Maven y JDK completo.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY simulator/pom.xml simulator/
COPY backend/pom.xml backend/
# Descargar dependencias en una capa propia: se reutiliza mientras no cambien los pom.xml.
RUN ./mvnw -B -q dependency:go-offline -DskipTests || true
COPY simulator/src simulator/src
COPY backend/src backend/src
RUN ./mvnw -B -q package -DskipTests

# Etapa 2: imagen final pequeña, solo con el JRE y el jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 nexus
COPY --from=build /app/backend/target/backend-*.jar app.jar
USER nexus
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
