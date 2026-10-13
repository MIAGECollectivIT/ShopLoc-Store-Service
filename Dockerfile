# ==========================================
# Étape 1 : Build de l'application (Maven + Eclipse Temurin 21)
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Mise en cache des dépendances Maven
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compilation et empaquetage du livrable jar
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Étape 2 : Image d'exécution minimale ARM64 / Multi-Arch
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Sécurité : Exécution sous un utilisateur non-root dédié
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copie du jar exécutable depuis le builder
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

USER appuser

# Port d'écoute standard
EXPOSE 8080

# Options JVM optimisées pour conteneurs (K3s / cgroups)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
