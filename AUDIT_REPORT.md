# AUDIT_REPORT — ShopLoc-Store-Service (`Store`)

> Audit en lecture seule. Aucun fichier source modifié. Chemins relatifs à `src/main/java/com/shoploc/store_service/` (noté `…/`).

## 1. Carte d'identité & Baseline

| Élément | Valeur |
|---|---|
| Build | Maven (`mvnw`), Java 21, Spring Boot **4.1.1** |
| Persistance | PostgreSQL + JPA, Flyway, H2 en test |
| Kafka | Non |
| Sécurité | JWT (jjwt 0.12.6) validé localement par `security/JwtValidatorService` + `RoleInterceptor` (pas de Spring Security) |
| Clients HTTP | `RestClient` (`client/HttpUserClient.java`) vers `users-service`, `java.net.http.HttpClient` (`service/GeocodingService.java`), AWS SDK v2 S3 (MinIO) |
| Observabilité | `spring-boot-starter-actuator` ✅ avec probes ; pas de tracing |
| Qualité | PMD + Spotless + JaCoCo configurés (`pom.xml:~200`) |
| Taille | ~6 700 lignes Java ; **tous les contrôleurs/entités/services sont dans le package racine** |
| Compilation | `./mvnw test-compile` : ✅ OK |
| Démarrage | **Non mesuré** (PostgreSQL + MinIO non lancés) ; estimation statique cf. §2 |

## 2. Problèmes détectés

### 🔴 Critique

**C1 — Credentials S3/MinIO par défaut en dur dans la config**
- `src/main/resources/application.properties:168-169` : `s3.access-key=${S3_ACCESS_KEY:devadmin}`, `s3.secret-key=${S3_SECRET_KEY:devpassword123}` ; idem DB `:147` (`devpassword`).
- Si la variable est omise en production, le service démarre avec des identifiants connus.
- Proposition : retirer les valeurs par défaut (`${S3_SECRET_KEY}`) hors profil `dev` → échec rapide plutôt que secret faible.

**C2 — `catch (Exception ignored)` vides / fallback HMAC sur la clé JWT**
- `…/security/JwtValidatorService.java:80-82` : en cas d'échec de parsing RSA, bascule silencieuse en **HMAC** avec la même chaîne configurée (`JWT_PUBLIC_KEY`). Risque d'*algorithm confusion* : une clé publique (connue) utilisée comme secret HMAC permettrait de forger des jetons.
- `…/storage/S3ImageStorageService.java:243-244` : `catch (Exception ignored) {}` complètement vide.
- Proposition : refuser le démarrage si la clé n'est pas RSA valide, supprimer le fallback HMAC ; logger l'exception en S3.

### 🟠 Majeur

**M1 — Association `EAGER` + risque N+1 / produit cartésien**
- `…/Store.java:81-86` : `@OneToMany(... fetch = FetchType.EAGER) List<StoreAbsence> absences`. Chaque `findAll`/`findByStatus` (`StoreRepository.java:14-22`) charge toutes les absences ; `StoreController.getAllStores` (`:41-58`) itère sur chaque `Store`.
- Proposition : `LAZY` + `@EntityGraph(attributePaths="absences")` uniquement sur les requêtes qui en ont besoin.

**M2 — Listes sans pagination**
- `…/StoreController.java:41` (`GET /v1/stores` : tous les commerces), `:214` (absences), `…/ProductController.java:39` (produits d'un commerce), `ProductRepository.java:16`.
- Proposition : `Pageable` + `Page<…Response>` (réutiliser `PageResponse` du service User).

**M3 — Aucun timeout sur `RestClient`**
- `…/client/HttpUserClient.java:27-42` : `RestClient.builder()` (`config/RestClientConfig.java:28`) sans `requestFactory` → pas de connect/read timeout (JDK `HttpClient` par défaut = infini en lecture). Un `users-service` lent bloque les threads Tomcat. (`GeocodingService.java:47-52` définit bien un `TIMEOUT` ✅.)
```java
// Après
var factory = new JdkClientHttpRequestFactory(
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
factory.setReadTimeout(Duration.ofSeconds(5));
restClientBuilder.requestFactory(factory)...
```

**M4 — Gestion d'erreurs non conforme RFC 7807**
- `…/exception/GlobalExceptionHandler.java:14-60` : `@RestControllerAdvice` présent mais renvoie des `Map<String,Object>` artisanales (`timestamp/status/error/message`) — pas de `ProblemDetail`. Incohérent avec le service User (qui utilise `ProblemDetail`).
- Proposition : `ProblemDetail.forStatusAndDetail(...)` + `spring.mvc.problemdetails.enabled=true`.

**M5 — Dockerfile sans AOT / AppCDS ; pas de réglage de démarrage**
- `Dockerfile` (stage runner) : `java -jar app.jar`. Boot ≥ 3 / Java 21 permettent `-Dspring.aot.enabled=true` + AppCDS (gain ≈ 30-40 % de démarrage).
- `application.properties` : pas de `spring.datasource.hikari.*` ni `spring.main.lazy-initialization` ; dialecte déclaré (`:152`) mais `hibernate.temp.use_jdbc_metadata_defaults=false` absent (appel métadonnées JDBC au démarrage).
- Positif : `ddl-auto=validate` (`:151`), `open-in-view=false` (`:153`).

**M6 — Pas de traçabilité distribuée**
- `pom.xml` : aucun `micrometer-tracing`. Les appels Store → User ne sont pas corrélés.

**M7 — Contrôleurs / endpoints : verbes et sémantique**
- `…/StoreController.java:270` `DELETE /{storeUid}/absence` coexiste avec `:256` `DELETE /{storeUid}/absences/{absenceUid}` (deux contrats pour la même ressource) ; `POST /{storeUid}/revoke`/`reactivate` (`:283`, `:301`) = verbes dans l'URL. Sans préfixe `/api/v1` (contrairement à User qui expose les deux).

### 🟡 Mineur / Ergonomie

**m1 — Injection par champ `@Autowired`**
- `…/ProductService.java:46`, `…/ItineraryController.java:28`, `…/storage/S3ImageStorageService.java:40`, `…/service/GeocodingService.java:48`, `…/service/ItineraryOptimizationService.java:43` (sur constructeurs pour certains : vérifier les lignes ; les doubles constructeurs rendent le choix ambigu).

**m2 — `@Value` (13 occurrences), aucun `@ConfigurationProperties`**
- Config `s3.*` (7 clés) et `services.users.url` ⇒ `@ConfigurationProperties("s3") @Validated record S3Properties(...)`.

**m3 — Structure de packages plate**
- `Store.java`, `Product.java`, `StoreController.java`, `ProductService.java`… dans `com.shoploc.store_service` : couches `controller/service/repository/entity/dto` à créer (lisibilité, scan ciblé). `@SpringBootApplication` scanne tout le package racine (ok vu la taille).

**m4 — Dépendances (`dependency:analyze`)**
- Unused (faux positifs : starters, `jjwt-impl`), mais réels à contrôler : `spring-boot-starter-mail` (usage à confirmer), `software.amazon.awssdk:s3` (utilisé) ; « used undeclared » : `jakarta.persistence-api`, `jakarta.mail-api`, `awssdk:{regions,auth,sdk-core,aws-core}`, `swagger-annotations-jakarta`.
- springdoc 2.8.5 (ligne Boot 3.x) avec Boot 4.1.1 : vérifier compatibilité.
- Doublon HTTP : `RestClient` + `java.net.http.HttpClient` (GeocodingService) : uniformiser.

**m5 — Autres points conformes ✅**
- Entités JPA non exposées (DTO `*Response`, `StoreResponse.fromEntity`), `@Valid` présent sur les contrôleurs, springdoc, probes K8s (`application.properties:177-180`), pas de `printStackTrace`. `management.endpoint.health.show-details=always` (`:180`) expose des détails (DB/disque) publiquement → passer à `when-authorized`.

## 3. Plan d'action priorisé

| # | Action | Gain | Effort |
|---|---|---|---|
| 1 | Supprimer fallback HMAC + vider `catch` silencieux (C2) | Élimine un vecteur de forge JWT | Faible |
| 2 | Retirer secrets S3/DB par défaut (C1) | Pas de démarrage avec creds faibles | Faible |
| 3 | Timeouts `RestClient` (M3) | Résilience / pas de threads bloqués | Faible |
| 4 | `Store.absences` en LAZY + `@EntityGraph` ciblé (M1) | Moins de requêtes/mémoire | Moyen |
| 5 | Pagination `stores`, `products`, `absences` (M2) | Scalabilité | Moyen |
| 6 | Migrer vers `ProblemDetail` (M4) | Cohérence inter-services | Faible |
| 7 | AOT + AppCDS + Hikari + metadata JDBC (M5) | −30-40 % démarrage | Moyen |
| 8 | `micrometer-tracing` (M6) | Traçabilité bout en bout | Faible |
| 9 | Unifier endpoints absences / préfixe `/api/v1` (M7) | API cohérente | Moyen |
| 10 | Injection constructeur + `@ConfigurationProperties` (m1, m2) | Maintenabilité | Moyen |
| 11 | Restructurer en packages par couche (m3) | Lisibilité | Moyen |
| 12 | `show-details=when-authorized` (m5) | Réduit fuite d'info | Faible |


## 4. Annexe — Build Docker (mesuré, build à froid `--no-cache`)

| Étape | Durée |
|---|---|
| `RUN mvn dependency:go-offline -B` (builder 4/6) | **53.3 s** (≈ 1473 artefacts téléchargés) |
| `RUN mvn clean package -DskipTests` (builder 6/6) | 4.4 s |
| Copie JAR + image finale | ~2 s (image 444 Mo) |
| **Total** | **61 s** |

- 🟠 **D1 — `dependency:go-offline` re-télécharge tout à chaque invalidation du cache** (`Dockerfile`, `RUN mvn dependency:go-offline -B`) : il ramène aussi des plugins/dépendances jamais utilisés au build. Aucun cache Maven persistant entre builds. Le cache de couche saute dès que `pom.xml` change, ou après `--no-cache` / prune.
- Proposition (gain estimé : ~50-70 s → ~5-10 s sur rebuild après modif du `pom.xml`) :
```dockerfile
# syntax=docker/dockerfile:1.7
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:resolve-plugins dependency:resolve
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -o clean package -DskipTests
```
- `mvnw`/`.mvn` copiés dans le contexte mais non utilisés : le conteneur utilise l'image `maven:3.9` (OK, mais version Maven non épinglée au wrapper).
- Compilation seule : ~4 s → le build applicatif n'est pas le problème, seul le téléchargement des dépendances l'est.
