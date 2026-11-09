package com.shoploc.store_service.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtValidatorService {

  private static final Logger log = LoggerFactory.getLogger(JwtValidatorService.class);
  private static final int MIN_KEY_BYTES = 32;
  private static final long REVOCATION_CACHE_TTL_MS = 15000;

  @Value("${application.security.jwt.public-key:}")
  private String publicKeyString;

  @Value("${application.security.jwt.secret-key:}")
  private String secretKeyString;

  @Value("${services.users.url:http://users-service:8080}")
  private String usersServiceUrl;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private Key verificationKey;
  private final Set<String> cachedRevokedJtis =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private volatile long lastRevocationFetchTime;

  @PostConstruct
  public void init() {
    String keyToUse =
        (publicKeyString != null && !publicKeyString.isBlank()) ? publicKeyString : secretKeyString;

    if (keyToUse != null && !keyToUse.isBlank()) {
      parseConfiguredKey(keyToUse);
    } else {
      // Attempt dynamic retrieval of public key from users-service at startup
      fetchPublicKeyFromUsersService();
    }
  }

  private void parseConfiguredKey(String keyToUse) {
    String clean = cleanKeyPem(keyToUse);
    try {
      byte[] decoded = Base64.getDecoder().decode(clean);
      if (decoded.length > 200) {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        this.verificationKey = kf.generatePublic(new X509EncodedKeySpec(decoded));
        log.info("JWT Validator initialisé avec clé publique RSA asymétrique (RS256).");
        return;
      }
    } catch (Exception ignored) {
      // Not a valid Base64 RSA key, fallback to HMAC
    }

    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(keyToUse.trim());
      if (keyBytes.length < MIN_KEY_BYTES) {
        keyBytes = keyToUse.trim().getBytes(StandardCharsets.UTF_8);
      }
    } catch (IllegalArgumentException notBase64) {
      keyBytes = keyToUse.trim().getBytes(StandardCharsets.UTF_8);
    }
    this.verificationKey = Keys.hmacShaKeyFor(keyBytes);
    log.info("JWT Validator initialisé avec clé symétrique HMAC.");
  }

  private synchronized void fetchPublicKeyFromUsersService() {
    if (usersServiceUrl == null || usersServiceUrl.isBlank()) {
      return;
    }
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      String endpoint = usersServiceUrl.replaceAll("/+$", "") + "/v1/auth/public-key";
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(endpoint))
              .timeout(Duration.ofSeconds(2))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode pubKeyNode = root.get("publicKey");
        if (pubKeyNode != null && pubKeyNode.isTextual() && !pubKeyNode.asText().isBlank()) {
          String clean = cleanKeyPem(pubKeyNode.asText());
          byte[] decoded = Base64.getDecoder().decode(clean);
          KeyFactory kf = KeyFactory.getInstance("RSA");
          this.verificationKey = kf.generatePublic(new X509EncodedKeySpec(decoded));
          log.info("Clé publique RSA récupérée dynamiquement depuis {}", endpoint);
        }
      }
    } catch (Exception e) {
      log.debug(
          "Impossible de récupérer la clé publique depuis users-service au démarrage : {}",
          e.getMessage());
    }
  }

  private String cleanKeyPem(String keyPem) {
    return keyPem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replaceAll("\\s+", "");
  }

  public Claims validateAndExtractClaims(String token) {
    if (verificationKey == null) {
      fetchPublicKeyFromUsersService();
    }
    if (verificationKey == null) {
      throw new UnauthorizedException("Clé de vérification JWT non configurée ou indisponible.");
    }

    Claims claims;
    try {
      var builder = Jwts.parser();
      if (verificationKey instanceof PublicKey pk) {
        builder.verifyWith(pk);
      } else if (verificationKey instanceof SecretKey sk) {
        builder.verifyWith(sk);
      }
      claims = builder.build().parseSignedClaims(token).getPayload();
    } catch (JwtException | IllegalArgumentException ex) {
      throw new UnauthorizedException("Jeton JWT invalide ou expiré : " + ex.getMessage(), ex);
    }

    // Token revocation check
    String jti = claims.getId();
    if (jti != null && isJtiRevoked(jti)) {
      throw new UnauthorizedException("Jeton JWT révoqué. Veuillez vous reconnecter.");
    }

    return claims;
  }

  private boolean isJtiRevoked(String jti) {
    long now = System.currentTimeMillis();
    // Avoid empty cache pitfall: refresh strictly according to TTL
    if (now - lastRevocationFetchTime > REVOCATION_CACHE_TTL_MS) {
      refreshRevokedTokens();
    }
    return cachedRevokedJtis.contains(jti);
  }

  public synchronized void refreshRevokedTokens() {
    if (usersServiceUrl == null || usersServiceUrl.isBlank()) {
      return;
    }
    lastRevocationFetchTime = System.currentTimeMillis();
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      String endpoint = usersServiceUrl.replaceAll("/+$", "") + "/v1/auth/revoked-tokens";
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(endpoint))
              .timeout(Duration.ofSeconds(2))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        Set<String> newRevoked = new HashSet<>();
        JsonNode root = objectMapper.readTree(response.body());
        if (root.isArray()) {
          for (JsonNode node : root) {
            if (node.isTextual() && !node.asText().isBlank()) {
              newRevoked.add(node.asText().trim());
            }
          }
        }
        cachedRevokedJtis.clear();
        cachedRevokedJtis.addAll(newRevoked);
        log.debug(
            "Liste des jetons révoqués rafraîchie : {} jetons actifs.", cachedRevokedJtis.size());
      }
    } catch (Exception ex) {
      log.debug("Impossible de rafraîchir la liste des jetons révoqués : {}", ex.getMessage());
    }
  }

  public String extractRole(Claims claims) {
    String role = claims.get("role", String.class);
    if (role == null) {
      role = claims.get("userRole", String.class);
    }
    return role != null ? role : "";
  }

  public void addRevokedJtiForTest(String jti) {
    cachedRevokedJtis.add(jti);
  }
}
