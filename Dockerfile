FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY simulator/pom.xml simulator/
COPY backend/pom.xml backend/
RUN ./mvnw -B -q dependency:go-offline -DskipTests || true
COPY simulator/src simulator/src
COPY backend/src backend/src
COPY frontend frontend
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 nexus
COPY --from=build /app/backend/target/backend-*.jar app.jar
USER nexus
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
