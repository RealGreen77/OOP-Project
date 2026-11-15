# ---------- build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/itsm-backend-*.jar app.jar

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system itsm && useradd --system --gid itsm itsm
COPY --from=build /workspace/app.jar app.jar
USER itsm
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
