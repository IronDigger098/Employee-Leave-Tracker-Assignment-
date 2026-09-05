# =============================================================================
#  Backend image - multi-stage build.
#
#  Why two stages: the tools needed to BUILD a Java application (Maven, the full
#  JDK, the whole dependency cache) are not needed to RUN it. Stage 1 compiles and
#  produces a jar; stage 2 starts from a clean JRE image and copies in only that
#  jar. The result is a much smaller image with no compiler and no source code in
#  it - less to download, and less attack surface.
# =============================================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the pom FIRST, on its own, and resolve dependencies before copying source.
#
# Docker caches each instruction as a layer and reuses it while its inputs are
# unchanged. Because pom.xml changes far less often than the Java files, this
# ordering means editing a controller reuses the cached dependency layer instead
# of re-downloading every jar. Copying everything at once would throw that cache
# away on every single source edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# -DskipTests: this image is an artifact builder, not a CI server. Tests belong in
# a pipeline step, not baked into every image build.
RUN mvn -B clean package -DskipTests

# ---------- Stage 2: runtime ----------
# A JRE, not a JDK - we only need to run bytecode, not compile it.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user. A container process running as root that gets
# compromised has root inside the container, which is a far worse starting point
# for an attacker than an unprivileged account.
RUN groupadd --system spring && useradd --system --gid spring spring

# The jar is the ONLY thing carried over from the build stage. No source, no
# Maven, no ~/.m2 cache.
COPY --from=build /build/target/*.jar app.jar

USER spring

# Documentation for humans and tooling; it does not publish the port by itself -
# docker-compose.yml does that.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
