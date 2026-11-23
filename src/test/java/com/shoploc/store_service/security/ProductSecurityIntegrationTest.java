package com.shoploc.store_service.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.ProductController;
import com.shoploc.store_service.ProductService;
import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductResponse;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.exception.AccessDeniedException;
import com.shoploc.store_service.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
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
class ProductSecurityIntegrationTest {

  private static KeyPair testKeyPair;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private ProductService productService;

  @InjectMocks private ProductController productController;

  private JwtValidatorService jwtValidatorService;
  private RoleInterceptor roleInterceptor;

  @BeforeAll
  static void initKeys() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    testKeyPair = kpg.generateKeyPair();
  }

  private static final UUID STORE_UID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PRODUCT_UID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @BeforeEach
  void setUp() {
    jwtValidatorService = new JwtValidatorService();
    String pubKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
    ReflectionTestUtils.setField(jwtValidatorService, "publicKeyString", pubKeyBase64);
    jwtValidatorService.init();

    roleInterceptor = new RoleInterceptor(jwtValidatorService);

    mockMvc =
        MockMvcBuilders.standaloneSetup(productController)
            .addInterceptors(roleInterceptor)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private String createToken(String role, UUID userId) {
    return createToken(role, userId, 3600000L);
  }

  private String createToken(String role, UUID userId, long ttlMs) {
    var builder =
        Jwts.builder()
            .header()
            .keyId("test-key")
            .and()
            .id(UUID.randomUUID().toString())
            .subject(userId != null ? userId.toString() : "service-account")
            .issuer("shoploc-auth-service")
            .expiration(new Date(System.currentTimeMillis() + ttlMs))
            .claim("role", role)
            .claim("email", "test@test.fr");
    if (userId != null) {
      builder.claim("userId", userId.toString());
    }
    return builder.signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
  }

  @Test
  @DisplayName(
      "GET /v1/stores/{storeUid}/products is public and should return 200 OK without token")
  void shouldAllowPublicCatalogConsultation() throws Exception {
    when(productService.getProductsByStore(STORE_UID)).thenReturn(List.of());

    mockMvc.perform(get("/v1/stores/" + STORE_UID + "/products")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("POST /v1/stores/{storeUid}/products without token should return 401 Unauthorized")
  void shouldDenyCreateProductWithoutToken() throws Exception {
    ProductCreateRequest request =
        new ProductCreateRequest("REF-001", "Baguette", "Desc", new BigDecimal("1.20"), null, 10);

    mockMvc
        .perform(
            post("/v1/stores/" + STORE_UID + "/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /v1/stores/{storeUid}/products with CLIENT role should return 403 Forbidden")
  void shouldDenyCreateProductWithClientRole() throws Exception {
    String clientToken = createToken("CLIENT", UUID.randomUUID());
    ProductCreateRequest request =
        new ProductCreateRequest("REF-001", "Baguette", "Desc", new BigDecimal("1.20"), null, 10);

    mockMvc
        .perform(
            post("/v1/stores/" + STORE_UID + "/products")
                .header("Authorization", "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "POST /v1/stores/{storeUid}/products with MERCHANT role on owned store should succeed (201 Created)")
  void shouldAllowCreateProductWithMerchantRole() throws Exception {
    UUID merchantUid = UUID.randomUUID();
    String merchantToken = createToken("MERCHANT", merchantUid);
    ProductCreateRequest request =
        new ProductCreateRequest("REF-001", "Baguette", "Desc", new BigDecimal("1.20"), null, 10);

    ProductResponse response =
        new ProductResponse(
            PRODUCT_UID,
            STORE_UID,
            "REF-001",
            "Baguette",
            "Desc",
            new BigDecimal("1.20"),
            null,
            10,
            Instant.now(),
            Instant.now());
    when(productService.createProduct(eq(STORE_UID), any(), any(), eq(merchantUid), eq("MERCHANT")))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/v1/stores/" + STORE_UID + "/products")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uid").value(PRODUCT_UID.toString()))
        .andExpect(jsonPath("$.storeUid").value(STORE_UID.toString()))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.storeId").doesNotExist())
        .andExpect(jsonPath("$.name").value("Baguette"));
  }

  @Test
  @DisplayName("POST /v1/stores/{storeUid}/products with ADMIN role should succeed on any store")
  void shouldAllowCreateProductWithAdminRole() throws Exception {
    UUID adminUid = UUID.randomUUID();
    String adminToken = createToken("ADMIN", adminUid);
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-ADMIN-01", "Boutique", "Desc", new BigDecimal("2.50"), null, 5);

    ProductResponse response =
        new ProductResponse(
            PRODUCT_UID,
            STORE_UID,
            "REF-ADMIN-01",
            "Boutique",
            "Desc",
            new BigDecimal("2.50"),
            null,
            5,
            Instant.now(),
            Instant.now());
    when(productService.createProduct(eq(STORE_UID), any(), any(), eq(adminUid), eq("ADMIN")))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/v1/stores/" + STORE_UID + "/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uid").value(PRODUCT_UID.toString()));
  }

  @Test
  @DisplayName(
      "PUT /v1/stores/{storeUid}/products/{productUid} without token should return 401 Unauthorized")
  void shouldDenyUpdateProductWithoutToken() throws Exception {
    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-01", "Nouveau nom", "Desc", new BigDecimal("2.00"), null, 10);

    mockMvc
        .perform(
            put("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "PUT /v1/stores/{storeUid}/products/{productUid} with CLIENT role should return 403 Forbidden")
  void shouldDenyUpdateProductWithClientRole() throws Exception {
    String clientToken = createToken("CLIENT", UUID.randomUUID());
    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-01", "Nom", "Desc", new BigDecimal("2.00"), null, 10);

    mockMvc
        .perform(
            put("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID)
                .header("Authorization", "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "PUT /v1/stores/{storeUid}/products/{productUid} with MERCHANT role on owned store should succeed (200 OK)")
  void shouldAllowUpdateProductWithMerchantRole() throws Exception {
    UUID merchantUid = UUID.randomUUID();
    String merchantToken = createToken("MERCHANT", merchantUid);
    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-01", "Nom Modifié", "Desc", new BigDecimal("2.50"), null, 15);

    ProductResponse response =
        new ProductResponse(
            PRODUCT_UID,
            STORE_UID,
            "REF-01",
            "Nom Modifié",
            "Desc",
            new BigDecimal("2.50"),
            null,
            15,
            Instant.now(),
            Instant.now());
    when(productService.updateProduct(
            eq(STORE_UID), eq(PRODUCT_UID), any(), any(), eq(merchantUid), eq("MERCHANT")))
        .thenReturn(response);

    mockMvc
        .perform(
            put("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID)
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Nom Modifié"));
  }

  @Test
  @DisplayName(
      "DELETE /v1/stores/{storeUid}/products/{productUid} without token should return 401 Unauthorized")
  void shouldDenyDeleteProductWithoutToken() throws Exception {
    mockMvc
        .perform(delete("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "DELETE /v1/stores/{storeUid}/products/{productUid} with CLIENT role should return 403 Forbidden")
  void shouldDenyDeleteProductWithClientRole() throws Exception {
    String clientToken = createToken("CLIENT", UUID.randomUUID());

    mockMvc
        .perform(
            delete("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID)
                .header("Authorization", "Bearer " + clientToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "DELETE /v1/stores/{storeUid}/products/{productUid} by non-owner merchant should return 403 Forbidden")
  void shouldDenyDeleteProductForNonOwnerMerchant() throws Exception {
    UUID otherMerchantUid = UUID.randomUUID();
    String merchantToken = createToken("MERCHANT", otherMerchantUid);
    doThrow(new AccessDeniedException("Accès refusé"))
        .when(productService)
        .deleteProduct(eq(STORE_UID), eq(PRODUCT_UID), eq(otherMerchantUid), eq("MERCHANT"));

    mockMvc
        .perform(
            delete("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID)
                .header("Authorization", "Bearer " + merchantToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "DELETE /v1/stores/{storeUid}/products/{productUid} by owner merchant should return 204 No Content")
  void shouldAllowMerchantToDeleteProduct() throws Exception {
    String merchantToken = createToken("MERCHANT", UUID.randomUUID());

    mockMvc
        .perform(
            delete("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID)
                .header("Authorization", "Bearer " + merchantToken))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeUid}/products/{productUid}/stock without token should return 401 Unauthorized")
  void shouldDenyPatchStockWithoutToken() throws Exception {
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-1);

    mockMvc
        .perform(
            patch("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID + "/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeUid}/products/{productUid}/stock with expired token should return 401 Unauthorized")
  void shouldDenyPatchStockWithExpiredToken() throws Exception {
    String expiredToken = createToken("SERVICE", null, -10000L);
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-1);

    mockMvc
        .perform(
            patch("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID + "/stock")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeUid}/products/{productUid}/stock with SERVICE role (M2M) should return 200 OK")
  void shouldAllowServiceRoleToPatchStock() throws Exception {
    String serviceToken = createToken("SERVICE", null);
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-2);

    ProductResponse response =
        new ProductResponse(
            PRODUCT_UID,
            STORE_UID,
            "REF-001",
            "Croissant",
            "Desc",
            new BigDecimal("1.50"),
            null,
            18,
            Instant.now(),
            Instant.now());
    when(productService.updateStock(eq(STORE_UID), eq(PRODUCT_UID), any(), any(), eq("SERVICE")))
        .thenReturn(response);

    mockMvc
        .perform(
            patch("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID + "/stock")
                .header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(18));
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeUid}/products/{productUid}/stock with MERCHANT role on owned store should succeed")
  void shouldAllowMerchantToPatchStockOnOwnedStore() throws Exception {
    UUID merchantUid = UUID.randomUUID();
    String merchantToken = createToken("MERCHANT", merchantUid);
    StockUpdateRequest request = StockUpdateRequest.ofAbsolute(30);

    ProductResponse response =
        new ProductResponse(
            PRODUCT_UID,
            STORE_UID,
            "REF-001",
            "Croissant",
            "Desc",
            new BigDecimal("1.50"),
            null,
            30,
            Instant.now(),
            Instant.now());
    when(productService.updateStock(
            eq(STORE_UID), eq(PRODUCT_UID), any(), eq(merchantUid), eq("MERCHANT")))
        .thenReturn(response);

    mockMvc
        .perform(
            patch("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID + "/stock")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(30));
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeUid}/products/{productUid}/stock with CLIENT role should be denied (403 Forbidden)")
  void shouldDenyClientFromPatchingStock() throws Exception {
    String clientToken = createToken("CLIENT", UUID.randomUUID());
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-1);

    mockMvc
        .perform(
            patch("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID + "/stock")
                .header("Authorization", "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "Anti-spoofing: CLIENT token injecting X-User-Role: ADMIN on POST should be rejected (403 Forbidden)")
  void shouldRejectHeaderSpoofingWhenClientInjectsAdminHeaderOnCreate() throws Exception {
    String clientToken = createToken("CLIENT", UUID.randomUUID());
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-SPOOF-01", "Article Piraté", "Desc", new BigDecimal("10.00"), null, 5);

    mockMvc
        .perform(
            post("/v1/stores/" + STORE_UID + "/products")
                .header("Authorization", "Bearer " + clientToken)
                .header("X-User-Role", "ADMIN")
                .header("Role", "ADMIN")
                .header("X-Forwarded-User", "admin")
                .header("X-Authenticated-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("Forbidden"));
  }

  @Test
  @DisplayName(
      "Anti-spoofing: CLIENT token injecting X-User-Role: MERCHANT on DELETE should be rejected (403 Forbidden)")
  void shouldRejectHeaderSpoofingWhenClientInjectsMerchantHeaderOnDelete() throws Exception {
    String clientToken = createToken("CLIENT", UUID.randomUUID());

    mockMvc
        .perform(
            delete("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID)
                .header("Authorization", "Bearer " + clientToken)
                .header("X-User-Role", "MERCHANT")
                .header("X-User-Db-Id", "100")
                .header("X-Authenticated-User-Id", "100"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  @DisplayName(
      "Anti-spoofing: anonymous request with spoofed X-User-Role: ADMIN headers should be rejected (401 Unauthorized)")
  void shouldRejectHeaderSpoofingWhenAnonymousInjectsAdminHeader() throws Exception {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-SPOOF-02", "Piratage Anonyme", "Desc", new BigDecimal("5.00"), null, 2);

    mockMvc
        .perform(
            post("/v1/stores/" + STORE_UID + "/products")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("Unauthorized"));
  }

  @Test
  @DisplayName(
      "Anti-spoofing: CLIENT token injecting X-User-Role: SERVICE on stock PATCH should be rejected (403 Forbidden)")
  void shouldRejectHeaderSpoofingWhenClientInjectsServiceHeaderOnStock() throws Exception {
    String clientToken = createToken("CLIENT", UUID.randomUUID());
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-5);

    mockMvc
        .perform(
            patch("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID + "/stock")
                .header("Authorization", "Bearer " + clientToken)
                .header("X-User-Role", "SERVICE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  @DisplayName(
      "GET /v1/stores/1/products and GET /v1/stores/{uid}/products/10 -> should reject numeric IDs with 400 Bad Request")
  void shouldRejectNumericStoreAndProductIdsWith400() throws Exception {
    mockMvc.perform(get("/v1/stores/1/products")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/v1/stores/" + STORE_UID + "/products/10"))
        .andExpect(status().isBadRequest());
  }
}
