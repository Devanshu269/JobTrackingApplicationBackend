# ---------- build ----------
# Pinned to 17 to match <java.version> in pom.xml. Render has no native Java runtime, so the
# image controls the JDK — without pinning, a base-image bump could silently change it.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies resolve in their own layer, keyed only on pom.xml. Source changes then rebuild
# without re-downloading the world — the difference between a ~30s and a ~4min deploy.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# -DskipTests is honest today because there is no test suite. Remove it the moment one exists,
# so a broken build fails here rather than in production.
RUN mvn -B -DskipTests clean package

# ---------- run ----------
# JRE, not JDK: smaller image and no compiler shipped to production.
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user. If the process is ever compromised it should not own the filesystem.
RUN useradd --system --uid 1001 --create-home appuser
USER appuser

COPY --from=build --chown=appuser:appuser /build/target/*.jar app.jar

# Render injects PORT; application-prod.yaml already binds ${PORT:8080}.
EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then sizes the heap from the container's
# actual limit, so this works unchanged on Render's 512MB free tier and on a larger paid plan.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
