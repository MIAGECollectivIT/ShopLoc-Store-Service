package com.shoploc.store_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shoploc.store_service.exception.UnauthorizedException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Garantit l'absence de repli HMAC (algorithm confusion) sur la validation JWT. */
class JwtValidatorServiceHardeningTest {

  private static KeyPair keyPair;
  private static String publicKeyBase64;

  @BeforeAll
  static void initKeys() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    keyPair = kpg.generateKeyPair();
    publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
  }

  private static JwtValidatorService serviceWithKey(String key) {
    JwtValidatorService service = new JwtValidatorService();
    ReflectionTestUtils.setField(service, "publicKeyString", key);
    ReflectionTestUtils.setField(service, "usersServiceUrl", "");
    return service;
  }

  @Test
  void invalidConfiguredKeyFailsFastInsteadOfFallingBackToHmac() {
    JwtValidatorService service =
        serviceWithKey("not-a-rsa-key-but-a-long-shared-secret-value-0123456789");

    assertThatThrownBy(service::init)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_PUBLIC_KEY");
  }

  @Test
  void validRs256TokenIsAccepted() {
    JwtValidatorService service = serviceWithKey(publicKeyBase64);
    service.init();
    String token =
        Jwts.builder()
            .subject("u1")
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
            .compact();

    assertThat(service.validateAndExtractClaims(token).getSubject()).isEqualTo("u1");
  }

  @Test
  void tokenSignedWithHmacUsingThePublicKeyBytesIsRejected() {
    JwtValidatorService service = serviceWithKey(publicKeyBase64);
    service.init();
    SecretKey forged = Keys.hmacShaKeyFor(keyPair.getPublic().getEncoded());
    String token =
        Jwts.builder()
            .subject("attacker")
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(forged)
            .compact();

    assertThatThrownBy(() -> service.validateAndExtractClaims(token))
        .isInstanceOf(UnauthorizedException.class);
  }
}
