FROM gradle:9.5.1-jdk21 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle test bootJar -PexcludeTags=integration --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG OTEL_JAVAAGENT_VERSION=2.29.0
ADD https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent-${OTEL_JAVAAGENT_VERSION}.jar /app/opentelemetry-javaagent.jar
COPY --from=builder /app/build/libs/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar" \
    OTEL_SDK_DISABLED=true \
    OTEL_METRICS_EXPORTER=none \
    OTEL_LOGS_EXPORTER=none
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
