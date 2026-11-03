package com.shoploc.store_service.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.Store;
import com.shoploc.store_service.StoreController;
import com.shoploc.store_service.StoreService;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StoreSecurityIntegrationTest {

  private static KeyPair testKeyPair;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private StoreService storeService;

  @InjectMocks private StoreController storeController;

  private JwtValidatorService jwtValidatorService;
  private RoleInterceptor roleInterceptor;
  private PrivateKey signingKey;

  @BeforeAll
  static void initKeyPair() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    testKeyPair = kpg.generateKeyPair();
  }

  @BeforeEach
  void setUp() {
    jwtValidatorService = new JwtValidatorService();
    String publicKeyBase64 =
        Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
    ReflectionTestUtils.setField(jwtValidatorService, "publicKeyString", publicKeyBase64);
    jwtValidatorService.init();

    signingKey = testKeyPair.getPrivate();

    roleInterceptor = new RoleInterceptor(jwtValidatorService);

    mockMvc =
        MockMvcBuilders.standaloneSetup(storeController)
            .addInterceptors(roleInterceptor)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private String generateTestToken(String role, String email) {
    return generateTestToken(role, email, UUID.randomUUID().toString(), 101L);
  }

  private String generateTestToken(String role, String email, String jti, Long dbId) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .id(jti)
        .subject(UUID.randomUUID().toString())
        .issuer("shoploc-auth-service")
        .issuedAt(new Date(now))
        .expiration(new Date(now + 3600000))
        .claim("userId", UUID.randomUUID().toString())
        .claim("dbId", dbId)
        .claim("email", email)
        .claim("role", role)
        .claim("clientCode", "CL99999")
        .signWith(signingKey)
        .compact();
  }

  @Test
  @DisplayName("POST /v1/stores sans authentification -> doit renvoyer 401 Unauthorized")
  void shouldDenyStoreCreationWhenAuthMissing() throws Exception {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            101L);

    mockMvc
        .perform(
            post("/v1/stores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("Unauthorized"));
  }

  @Test
  @DisplayName(
      "POST /v1/stores avec X-User-Role: ADMIN sans JWT -> doit renvoyer 401 Unauthorized (pas de bypass)")
  void shouldDenyStoreCreationWhenXUserRoleBypassAttempted() throws Exception {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            101L);

    mockMvc
        .perform(
            post("/v1/stores")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  @DisplayName("POST /v1/stores avec JWT rôle CLIENT -> doit renvoyer 403 Forbidden")
  void shouldDenyStoreCreationWhenJwtRoleIsClient() throws Exception {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            101L);
    String clientToken = generateTestToken("CLIENT", "client@test.fr");

    mockMvc
        .perform(
            post("/v1/stores")
                .header("Authorization", "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Forbidden"));
  }

  @Test
  @DisplayName("POST /v1/stores avec JWT rôle MERCHANT -> doit autoriser la création (201 Created)")
  void shouldAllowStoreCreationWhenJwtRoleIsMerchant() throws Exception {
    Store store =
        new Store(
            "Boulangerie Artisanale",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            102L);
    when(storeService.createStore(any(StoreCreateRequest.class))).thenReturn(store);

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Boulangerie Artisanale",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            102L);
    String merchantToken =
        generateTestToken("MERCHANT", "boulanger@shoploc.fr", UUID.randomUUID().toString(), 102L);

    mockMvc
        .perform(
            post("/v1/stores")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Boulangerie Artisanale"))
        .andExpect(jsonPath("$.userId").value(102L));
  }

  @Test
  @DisplayName("POST /v1/stores avec JWT MERCHANT et userId omis (null) -> doit auto-assigner l'identifiant du token (201 Created)")
  void shouldAllowStoreCreationWhenMerchantOmitsUserIdInBody() throws Exception {
    Store store =
        new Store(
            "Boulangerie Sans UserId",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            102L);
    when(storeService.createStore(any(StoreCreateRequest.class))).thenReturn(store);

    // userId est null dans la requête du formulaire Front
    StoreCreateRequest requestWithoutUserId =
        new StoreCreateRequest(
            "Boulangerie Sans UserId",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            null);
    String merchantToken =
        generateTestToken("MERCHANT", "boulanger@shoploc.fr", UUID.randomUUID().toString(), 102L);

    mockMvc
        .perform(
            post("/v1/stores")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithoutUserId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Boulangerie Sans UserId"))
        .andExpect(jsonPath("$.userId").value(102L));
  }

  @Test
  @DisplayName(
      "POST /v1/stores par un MERCHANT tentant d'usurper un autre userId -> doit renvoyer 403 Forbidden (Anti-IDOR)")
  void shouldDenyStoreCreationWhenMerchantTriesToSpoofAnotherUserId() throws Exception {
    // Le commerçant connecté a dbId = 102L
    String merchantToken =
        generateTestToken("MERCHANT", "boulanger@shoploc.fr", UUID.randomUUID().toString(), 102L);

    // Mais il tente de soumettre un userId = 999L
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Boulangerie Artisanale",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            999L);

    mockMvc
        .perform(
            post("/v1/stores")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(
            jsonPath("$.message").value(org.hamcrest.Matchers.containsString("propre compte")));
  }

  @Test
  @DisplayName("POST /v1/stores avec JWT rôle ADMIN -> doit autoriser la création (201 Created)")
  void shouldAllowStoreCreationWhenJwtRoleIsAdmin() throws Exception {
    Store store =
        new Store(
            "Super Boutique Admin",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            101L);
    when(storeService.createStore(any(StoreCreateRequest.class))).thenReturn(store);

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique Admin",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            101L);
    String adminToken =
        generateTestToken("ADMIN", "admin@shoploc.fr", UUID.randomUUID().toString(), 101L);

    mockMvc
        .perform(
            post("/v1/stores")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Super Boutique Admin"))
        .andExpect(jsonPath("$.userId").value(101L));
  }

  @Test
  @DisplayName("POST /v1/stores avec token révoqué -> doit renvoyer 401 Unauthorized")
  void shouldDenyStoreCreationWithRevokedToken() throws Exception {
    String revokedJti = "revoked-uuid-" + UUID.randomUUID();
    jwtValidatorService.addRevokedJtiForTest(revokedJti);

    String token = generateTestToken("ADMIN", "admin@shoploc.fr", revokedJti, 1L);
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique Admin",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            101L);

    mockMvc
        .perform(
            post("/v1/stores")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  @DisplayName("GET /v1/stores avec JWT valide -> doit renvoyer 200 OK")
  void shouldAllowStoreConsultationWithValidJwt() throws Exception {
    when(storeService.getAllStores()).thenReturn(Collections.emptyList());
    String token = generateTestToken("CLIENT", "user@shoploc.fr");
    mockMvc
        .perform(get("/v1/stores").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /v1/stores sans JWT -> doit renvoyer 200 OK (consultation publique autorisée)")
  void shouldAllowStoreConsultationWithoutToken() throws Exception {
    when(storeService.getAllStores()).thenReturn(Collections.emptyList());
    mockMvc.perform(get("/v1/stores")).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "GET /v1/stores avec JWT révoqué -> doit renvoyer 200 OK (dégradation gracieuse en visiteur anonyme)")
  void shouldAllowStoreConsultationEvenWithRevokedToken() throws Exception {
    when(storeService.getAllStores()).thenReturn(Collections.emptyList());
    String revokedJti = "revoked-visitor-" + UUID.randomUUID();
    jwtValidatorService.addRevokedJtiForTest(revokedJti);

    String token = generateTestToken("CLIENT", "user@shoploc.fr", revokedJti, 50L);
    mockMvc
        .perform(get("/v1/stores").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "GET /v1/stores/{id} sans JWT -> doit renvoyer 200 OK (consultation publique du détail)")
  void shouldAllowStoreDetailConsultationWithoutToken() throws Exception {
    Store store =
        new Store(
            "Boutique Publique",
            "1 rue Publique",
            "https://img.com/p.jpg",
            "Mode",
            "FR7630006000011234567890189",
            1L);
    ReflectionTestUtils.setField(store, "id", 1L);
    when(storeService.getStoreById(1L)).thenReturn(Optional.of(store));

    mockMvc
        .perform(get("/v1/stores/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Boutique Publique"));
  }
}
