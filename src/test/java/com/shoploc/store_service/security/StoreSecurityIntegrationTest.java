package com.shoploc.store_service.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.controller.StoreController;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.entity.Store;
import com.shoploc.store_service.exception.GlobalExceptionHandler;
import com.shoploc.store_service.service.StoreService;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
    return generateTestToken(role, email, UUID.randomUUID().toString(), UUID.randomUUID());
  }

  private String generateTestToken(String role, String email, String jti, UUID userId) {
    long now = System.currentTimeMillis();
    var builder =
        Jwts.builder()
            .id(jti)
            .subject(userId != null ? userId.toString() : "service")
            .issuer("shoploc-auth-service")
            .issuedAt(new Date(now))
            .expiration(new Date(now + 3600000))
            .claim("email", email)
            .claim("role", role)
            .claim("clientCode", "CL99999");
    if (userId != null) {
      builder.claim("userId", userId.toString());
    }
    return builder.signWith(signingKey).compact();
  }

  @Test
  @DisplayName("POST /v1/stores without authentication -> should return 401 Unauthorized")
  void shouldDenyStoreCreationWhenAuthMissing() throws Exception {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            UUID.randomUUID());

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
      "POST /v1/stores with X-User-Role: ADMIN without JWT -> should return 401 Unauthorized (no bypass)")
  void shouldDenyStoreCreationWhenXUserRoleBypassAttempted() throws Exception {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            UUID.randomUUID());

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
  @DisplayName("POST /v1/stores with CLIENT role JWT -> should return 403 Forbidden")
  void shouldDenyStoreCreationWhenJwtRoleIsClient() throws Exception {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            UUID.randomUUID());
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
  @DisplayName("POST /v1/stores with MERCHANT role JWT -> should allow creation (201 Created)")
  void shouldAllowStoreCreationWhenJwtRoleIsMerchant() throws Exception {
    UUID merchantUid = UUID.randomUUID();
    Store store =
        new Store(
            "Boulangerie Artisanale",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            merchantUid);
    when(storeService.createStore(any(StoreCreateRequest.class))).thenReturn(store);

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Boulangerie Artisanale",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            merchantUid);
    String merchantToken =
        generateTestToken(
            "MERCHANT", "boulanger@shoploc.fr", UUID.randomUUID().toString(), merchantUid);

    mockMvc
        .perform(
            post("/v1/stores")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Boulangerie Artisanale"))
        .andExpect(jsonPath("$.ownerUid").value(merchantUid.toString()))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.userId").doesNotExist());
  }

  @Test
  @DisplayName(
      "POST /v1/stores with MERCHANT JWT and omitted (null) ownerUid -> should auto-assign token UUID (201 Created)")
  void shouldAllowStoreCreationWhenMerchantOmitsOwnerUidInBody() throws Exception {
    UUID merchantUid = UUID.randomUUID();
    Store store =
        new Store(
            "Boulangerie Sans OwnerUid",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            merchantUid);
    when(storeService.createStore(any(StoreCreateRequest.class))).thenReturn(store);

    StoreCreateRequest requestWithoutOwnerUid =
        new StoreCreateRequest(
            "Boulangerie Sans OwnerUid",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            null);
    String merchantToken =
        generateTestToken(
            "MERCHANT", "boulanger@shoploc.fr", UUID.randomUUID().toString(), merchantUid);

    mockMvc
        .perform(
            post("/v1/stores")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithoutOwnerUid)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Boulangerie Sans OwnerUid"))
        .andExpect(jsonPath("$.ownerUid").value(merchantUid.toString()));
  }

  @Test
  @DisplayName(
      "POST /v1/stores by MERCHANT attempting to spoof another ownerUid -> should return 403 Forbidden (Anti-IDOR)")
  void shouldDenyStoreCreationWhenMerchantTriesToSpoofAnotherOwnerUid() throws Exception {
    UUID merchantUid = UUID.randomUUID();
    String merchantToken =
        generateTestToken(
            "MERCHANT", "boulanger@shoploc.fr", UUID.randomUUID().toString(), merchantUid);

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Boulangerie Artisanale",
            "5 Place du Marché",
            "https://img.com/boulangerie.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            UUID.randomUUID());

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
  @DisplayName("POST /v1/stores with ADMIN role JWT -> should allow creation (201 Created)")
  void shouldAllowStoreCreationWhenJwtRoleIsAdmin() throws Exception {
    UUID adminUid = UUID.randomUUID();
    UUID targetOwnerUid = UUID.randomUUID();
    Store store =
        new Store(
            "Super Boutique Admin",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            targetOwnerUid);
    when(storeService.createStore(any(StoreCreateRequest.class))).thenReturn(store);

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique Admin",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            targetOwnerUid);
    String adminToken =
        generateTestToken("ADMIN", "admin@shoploc.fr", UUID.randomUUID().toString(), adminUid);

    mockMvc
        .perform(
            post("/v1/stores")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Super Boutique Admin"))
        .andExpect(jsonPath("$.ownerUid").value(targetOwnerUid.toString()));
  }

  @Test
  @DisplayName("POST /v1/stores with revoked token -> should return 401 Unauthorized")
  void shouldDenyStoreCreationWithRevokedToken() throws Exception {
    String revokedJti = "revoked-uuid-" + UUID.randomUUID();
    jwtValidatorService.addRevokedJtiForTest(revokedJti);

    String token = generateTestToken("ADMIN", "admin@shoploc.fr", revokedJti, UUID.randomUUID());
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Super Boutique Admin",
            "1 rue de la Paix",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            UUID.randomUUID());

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
  @DisplayName("GET /v1/stores/me with MERCHANT JWT -> should extract UUID userId claim")
  void shouldReturnMyStoresWithJwtUserIdUuid() throws Exception {
    UUID merchantUid = UUID.randomUUID();
    Store store =
        new Store(
            "Ma Boutique",
            "10 rue de Lille",
            "https://img.com/m.jpg",
            "Mode",
            "FR7630006000011234567890189",
            merchantUid);
    when(storeService.getStoresByOwnerUid(merchantUid)).thenReturn(List.of(store));

    String token =
        generateTestToken(
            "MERCHANT", "merchant@shoploc.fr", UUID.randomUUID().toString(), merchantUid);
    mockMvc
        .perform(get("/v1/stores/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].uid").value(store.getUid().toString()))
        .andExpect(jsonPath("$[0].ownerUid").value(merchantUid.toString()))
        .andExpect(jsonPath("$[0].id").doesNotExist())
        .andExpect(jsonPath("$[0].userId").doesNotExist());
  }

  @Test
  @DisplayName("GET /v1/stores with valid JWT -> should return 200 OK")
  void shouldAllowStoreConsultationWithValidJwt() throws Exception {
    when(storeService.getAllStores()).thenReturn(Collections.emptyList());
    String token = generateTestToken("CLIENT", "user@shoploc.fr");
    mockMvc
        .perform(get("/v1/stores").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /v1/stores without JWT -> should return 200 OK (public access allowed)")
  void shouldAllowStoreConsultationWithoutToken() throws Exception {
    when(storeService.getAllStores()).thenReturn(Collections.emptyList());
    mockMvc.perform(get("/v1/stores")).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "GET /v1/stores with revoked JWT -> should return 200 OK (graceful degradation to anonymous visitor)")
  void shouldAllowStoreConsultationEvenWithRevokedToken() throws Exception {
    when(storeService.getAllStores()).thenReturn(Collections.emptyList());
    String revokedJti = "revoked-visitor-" + UUID.randomUUID();
    jwtValidatorService.addRevokedJtiForTest(revokedJti);

    String token = generateTestToken("CLIENT", "user@shoploc.fr", revokedJti, UUID.randomUUID());
    mockMvc
        .perform(get("/v1/stores").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /v1/stores/{uid} without JWT -> should return 200 OK exposing uid without id")
  void shouldAllowStoreDetailConsultationWithoutToken() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID ownerUid = UUID.randomUUID();
    Store store =
        new Store(
            "Boutique Publique",
            "1 rue Publique",
            "https://img.com/p.jpg",
            "Mode",
            "FR7630006000011234567890189",
            ownerUid);
    ReflectionTestUtils.setField(store, "id", 1L);
    ReflectionTestUtils.setField(store, "uid", uid);
    when(storeService.getStoreByUid(uid)).thenReturn(Optional.of(store));

    mockMvc
        .perform(get("/v1/stores/" + uid))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Boutique Publique"))
        .andExpect(jsonPath("$.uid").value(uid.toString()))
        .andExpect(jsonPath("$.ownerUid").value(ownerUid.toString()))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.userId").doesNotExist());
  }

  @Test
  @DisplayName(
      "GET /v1/stores/{id} and GET /v1/stores?userId=123 with non-UUID -> should return 400 Bad Request")
  void shouldRejectNonUuidIdentifiersWith400() throws Exception {
    mockMvc.perform(get("/v1/stores/1")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/v1/stores/1/image")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/v1/stores").param("userId", "123")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "GET /v1/stores/{uid}/image without JWT -> should return 200 OK (public storefront image)")
  void shouldAllowStoreImageConsultationWithoutToken() throws Exception {
    UUID uid = UUID.randomUUID();
    byte[] imageBytes = new byte[] {0x11, 0x22};
    when(storeService.getStoreImage(uid))
        .thenReturn(new com.shoploc.store_service.storage.ImageResource(imageBytes, "image/jpeg"));

    mockMvc
        .perform(get("/v1/stores/" + uid + "/image"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/jpeg"))
        .andExpect(header().string("Cache-Control", "public, max-age=86400"));
  }

  @Test
  @DisplayName("PATCH /v1/stores/{uid} without JWT -> should return 401 Unauthorized")
  void shouldRejectPatchStoreWithoutToken() throws Exception {
    UUID uid = UUID.randomUUID();
    mockMvc
        .perform(
            patch("/v1/stores/" + uid)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"10 rue Neuve\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PATCH /v1/stores/{uid} with CLIENT role -> should return 403 Forbidden")
  void shouldRejectPatchStoreForClient() throws Exception {
    UUID uid = UUID.randomUUID();
    String token = generateTestToken("CLIENT", "client@shoploc.fr");
    mockMvc
        .perform(
            patch("/v1/stores/" + uid)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"10 rue Neuve\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PATCH /v1/stores/{uid} with MERCHANT role -> should return 200 OK")
  void shouldAllowPatchStoreForMerchant() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID ownerUid = UUID.randomUUID();
    Store store =
        new Store(
            "Boutique Test",
            "10 rue Neuve",
            "https://img.com/test.jpg",
            "Mode",
            "FR7630006000011234567890189",
            ownerUid);
    ReflectionTestUtils.setField(store, "id", 1L);
    ReflectionTestUtils.setField(store, "uid", uid);

    when(storeService.patchStore(any(), any(), any(), any())).thenReturn(store);

    String token = generateTestToken("MERCHANT", "merchant@shoploc.fr", "jti-patch-ok", ownerUid);
    mockMvc
        .perform(
            patch("/v1/stores/" + uid)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"10 rue Neuve\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value("10 rue Neuve"));
  }

  @Test
  @DisplayName("PATCH /v1/stores/{uid}/image without JWT -> should return 401 Unauthorized")
  void shouldRejectUpdateStoreImageWithoutToken() throws Exception {
    UUID uid = UUID.randomUUID();
    org.springframework.mock.web.MockMultipartFile imageFile =
        new org.springframework.mock.web.MockMultipartFile(
            "image", "vitrine.jpg", "image/jpeg", new byte[] {0x11, 0x22});

    mockMvc
        .perform(
            multipart(org.springframework.http.HttpMethod.PATCH, "/v1/stores/" + uid + "/image")
                .file(imageFile))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PATCH /v1/stores/{uid}/image with CLIENT role -> should return 403 Forbidden")
  void shouldRejectUpdateStoreImageForClient() throws Exception {
    UUID uid = UUID.randomUUID();
    org.springframework.mock.web.MockMultipartFile imageFile =
        new org.springframework.mock.web.MockMultipartFile(
            "image", "vitrine.jpg", "image/jpeg", new byte[] {0x11, 0x22});
    String token = generateTestToken("CLIENT", "client@shoploc.fr");

    mockMvc
        .perform(
            multipart(org.springframework.http.HttpMethod.PATCH, "/v1/stores/" + uid + "/image")
                .file(imageFile)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PATCH /v1/stores/{uid}/image with MERCHANT role -> should return 200 OK")
  void shouldAllowUpdateStoreImageForMerchant() throws Exception {
    UUID uid = UUID.randomUUID();
    UUID ownerUid = UUID.randomUUID();
    Store store =
        new Store(
            "Boutique Vitrine",
            "10 rue Neuve",
            "https://cdn.shoploc.fr/vitrine.jpg",
            "Mode",
            "FR7630006000011234567890189",
            ownerUid);
    ReflectionTestUtils.setField(store, "id", 1L);
    ReflectionTestUtils.setField(store, "uid", uid);

    when(storeService.updateStoreImage(any(), any(), any(), any())).thenReturn(store);

    org.springframework.mock.web.MockMultipartFile imageFile =
        new org.springframework.mock.web.MockMultipartFile(
            "image", "vitrine.jpg", "image/jpeg", new byte[] {0x11, 0x22});
    String token = generateTestToken("MERCHANT", "merchant@shoploc.fr", "jti-img-ok", ownerUid);

    mockMvc
        .perform(
            multipart(org.springframework.http.HttpMethod.PATCH, "/v1/stores/" + uid + "/image")
                .file(imageFile)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://cdn.shoploc.fr/vitrine.jpg"));
  }

  @Test
  @DisplayName("GET /v1/stores/search without JWT -> should return 200 OK (public access)")
  void shouldAllowStoreSearchWithoutToken() throws Exception {
    Store store =
        new Store(
            "Boulangerie Publique",
            "1 rue de la Recherche",
            "https://img.com/search.jpg",
            "Boulangerie",
            "FR7630006000011234567890189",
            UUID.randomUUID());
    when(storeService.searchStores(any(), any())).thenReturn(List.of(store));

    mockMvc
        .perform(
            get("/v1/stores/search").param("name", "Boulangerie").param("category", "Boulangerie"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Boulangerie Publique"))
        .andExpect(jsonPath("$[0].category").value("Boulangerie"))
        .andExpect(jsonPath("$[0].id").doesNotExist())
        .andExpect(jsonPath("$[0].userId").doesNotExist())
        .andExpect(jsonPath("$[0].uid").exists());
  }
}
