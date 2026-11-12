FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY common-library/pom.xml common-library/
COPY gateway-service/pom.xml gateway-service/
COPY event-service/pom.xml event-service/
COPY ai-service/pom.xml ai-service/
COPY booking-service/pom.xml booking-service/
COPY payment-service/pom.xml payment-service/
COPY user-service/pom.xml user-service/
COPY analytics-service/pom.xml analytics-service/
COPY start-application/pom.xml start-application/

COPY common-library/src common-library/src
COPY user-service/src user-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl user-service -am -DskipTests package

FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --home /app --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=build --chown=app:app /workspace/user-service/target/user-service-*.jar /app/app.jar

USER app

EXPOSE 8084

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8084' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
