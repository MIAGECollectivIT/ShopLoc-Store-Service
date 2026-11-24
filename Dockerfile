# ==========================================
# Étape 1 : Build de l'application (Maven + Eclipse Temurin 21)
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Mise en cache des dépendances Maven (cache BuildKit persistant, hors image finale)
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:resolve

# Compilation et empaquetage du livrable jar
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

# ==========================================
# Étape 2 : Image d'exécution minimale ARM64 / Multi-Arch
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Sécurité : Exécution sous un utilisateur non-root dédié
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && chown appuser:appgroup /app

# Copie du jar exécutable depuis le builder
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

USER appuser

# AppCDS : archive des classes chargées au démarrage, générée À LA CONSTRUCTION de l'image
# (donc pour l'architecture cible, ARM64 incluse). Entraînement sans base de données :
# le contexte Spring est rafraîchi puis arrêté (spring.context.exit=onRefresh).
# Non bloquant : si l'entraînement échoue, l'image reste valide (démarrage sans archive).
RUN java -XX:ArchiveClassesAtExit=/app/app.jsa \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dservices.users.url= \
      -jar app.jar > /tmp/cds-training.log 2>&1 \
    || (echo "AppCDS training failed (non bloquant):" && tail -n 20 /tmp/cds-training.log)

# Port d'écoute standard
EXPOSE 8080

# Options JVM optimisées pour conteneurs (K3s / cgroups) + AppCDS (-Xshare:auto = repli sûr)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
