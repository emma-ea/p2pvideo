# ── Stage 1: Build the WAR with Maven ─────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy POM first to cache dependency downloads separately from source
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Payara Micro 6 runtime ───────────────────────────────────────
FROM payara/micro:6.2024.6-jdk17

USER root

# Download PostgreSQL JDBC driver and make it readable by the payara user
ADD https://jdbc.postgresql.org/download/postgresql-42.7.3.jar \
    /opt/payara/libs/postgresql.jar
RUN chown payara:payara /opt/payara/libs/postgresql.jar

# Copy the pre-boot admin commands that configure the JDBC data source
# COPY payara-resources.xml /opt/payara/config/pre-boot-commands.asadmin

# Copy the WAR built in stage 1
COPY --from=build /build/target/facep2pv2-0.0.1.war \
                  /opt/payara/deployments/p2pvideo.war

USER payara

EXPOSE 2026

ENTRYPOINT ["java", "-jar", "/opt/payara/payara-micro.jar", \
            "--addLibs", "/opt/payara/libs/postgresql.jar", \
            "--deploy", "/opt/payara/deployments/p2pvideo.war", \
            "--port", "2026"]
