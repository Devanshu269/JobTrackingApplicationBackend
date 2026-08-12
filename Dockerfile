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

# Tuned for a 512 MB container. The numbers matter here — an earlier MaxRAMPercentage=75 gave the
# heap 384 MB and left ~128 MB for everything else, which Spring Boot's non-heap usage exceeds on
# its own. The container was OOM-killed during startup, the peak moment, when Flyway and Hibernate
# are both building metadata.
#
#   MaxRAMPercentage=50   heap ~256 MB, leaving ~256 MB for non-heap
#   MaxMetaspaceSize      caps class metadata, which is large here (Spring Security, JPA, OAuth2,
#                         Flyway, Cloudinary, actuator) and is otherwise unbounded
#   UseSerialGC           G1 keeps per-region bookkeeping that is pure overhead below ~1 GB;
#                         SerialGC is both smaller and faster to start on a single small heap
#   TieredStopAtLevel=1   C1 only. Less code cache and less JIT memory, and noticeably faster
#                         startup — which also shortens Render's cold start
#   Xss512k               default is 1 MB per thread; this halves it across the whole pool
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
