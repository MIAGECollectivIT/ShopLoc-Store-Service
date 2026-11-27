# syntax=docker/dockerfile:1.7
# ==========================================
# Étape 1 : Build de l'application (Maven + Eclipse Temurin 21)
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Mise en cache des dépendances Maven (cache BuildKit persistant, hors image finale)
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:resolve-plugins dependency:resolve

# Compilation des sources principales et extraction des couches Spring Boot (Layered JAR)
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 mvn -B package -Dmaven.test.skip=true \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# ==========================================
# Étape 2 : Image d'exécution minimale ARM64 / Multi-Arch
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Sécurité : Exécution sous un utilisateur non-root dédié
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copie ordonnée des couches Spring Boot (de la moins fréquente à la plus fréquente)
COPY --from=builder --chown=appuser:appgroup /build/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/extracted/application/ ./

USER appuser

# Port d'écoute standard
EXPOSE 8080

# Options JVM optimisées pour conteneurs (K3s / cgroups)
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=3s --start-period=25s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
