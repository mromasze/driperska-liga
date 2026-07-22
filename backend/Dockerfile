# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# --- runtime stage ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# gosu (static binary) lets the entrypoint fix volume ownership as root, then drop to the
# 'app' user. We copy it from the official image instead of apt-get, because fetching it
# from the Ubuntu archive in CI is slow and frequently fails ("Connection failed").
COPY --from=tianon/gosu:1.17 /usr/local/bin/gosu /usr/local/bin/gosu
# fontconfig + a base font are required for Java2D result-card rendering. Retry apt to
# ride out transient CI network hiccups; the base image already ships these packages.
RUN set -eux \
  && apt-get -o Acquire::Retries=5 update \
  && apt-get -o Acquire::Retries=5 install -y --no-install-recommends fontconfig fonts-dejavu-core \
  && rm -rf /var/lib/apt/lists/* \
  && useradd -r -u 1001 app && mkdir -p /app/data/media && chown -R app /app
COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
# NOTE: we intentionally start as root so the entrypoint can chown the mounted media
# volume; it drops to 'app' via gosu before launching the JVM.
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD ["sh","-c","wget -qO- http://localhost:8080/actuator/health || exit 1"]
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
