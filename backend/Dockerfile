FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/data/materials && chown -R app:app /app/data
COPY --from=build /app/target/ent-backend-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s CMD wget -q -O /dev/null http://127.0.0.1:8080/api/test/ping || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
