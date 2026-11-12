# syntax=docker/dockerfile:1
# Backend image: built from this directory (docker build -t hourforge-backend backend).
# Tests are not run here; they run before the image is built (locally or in CI).

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# A Windows checkout may give the wrapper CRLF line endings.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/hour-forge-*.jar /app.jar
# Layers ordered from the least to the most often changed, so an update of the code alone reuses the dependency layers.
RUN java -Djarmode=tools -jar /app.jar extract --layers --destination /extracted

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system --gid 10001 hourforge \
    && useradd --system --uid 10001 --gid hourforge --home-dir /app --shell /usr/sbin/nologin hourforge
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 10001:10001
ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8080
# /api/config is public and touches no session; Actuator comes later, on the internal network only.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
    CMD curl -fsS -o /dev/null http://127.0.0.1:8080/api/config || exit 1
# Configuration comes only from environment variables (see .env.example); no .env file is copied into the image.
# "docker run <image> migrate" applies the database migrations and exits (the deployment step before the application).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
