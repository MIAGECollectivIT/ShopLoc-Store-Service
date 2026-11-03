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

  @BeforeEach
  void setUp() {
    jwtValidatorService = new JwtValidatorService();
    String pubKeyBase64 =
        Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
    ReflectionTestUtils.setField(jwtValidatorService, "publicKeyString", pubKeyBase64);
    jwtValidatorService.init();

    roleInterceptor = new RoleInterceptor(jwtValidatorService);

    mockMvc =
        MockMvcBuilders.standaloneSetup(productController)
            .addInterceptors(roleInterceptor)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private String createToken(String role, Long dbId) {
    return createToken(role, dbId, 3600000L);
  }

  private String createToken(String role, Long dbId, long ttlMs) {
    return Jwts.builder()
        .header()
        .keyId("test-key")
        .and()
        .id(UUID.randomUUID().toString())
        .subject(UUID.randomUUID().toString())
        .issuer("shoploc-auth-service")
        .expiration(new Date(System.currentTimeMillis() + ttlMs))
        .claim("role", role)
        .claim("dbId", dbId)
        .claim("userId", UUID.randomUUID().toString())
        .claim("email", "test@test.fr")
        .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  @Test
  @DisplayName("GET /v1/stores/{storeId}/products est public et doit renvoyer 200 OK sans token")
  void shouldAllowPublicCatalogConsultation() throws Exception {
    when(productService.getProductsByStore(1L)).thenReturn(List.of());

    mockMvc.perform(get("/v1/stores/1/products")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("POST /v1/stores/{storeId}/products sans token doit renvoyer 401 Unauthorized")
  void shouldDenyCreateProductWithoutToken() throws Exception {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-001", "Baguette", "Desc", new BigDecimal("1.20"), null, 10);

    mockMvc
        .perform(
            post("/v1/stores/1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /v1/stores/{storeId}/products avec rôle CLIENT doit renvoyer 403 Forbidden")
  void shouldDenyCreateProductWithClientRole() throws Exception {
    String clientToken = createToken("CLIENT", 42L);
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-001", "Baguette", "Desc", new BigDecimal("1.20"), null, 10);

    mockMvc
        .perform(
            post("/v1/stores/1/products")
                .header("Authorization", "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /v1/stores/{storeId}/products avec rôle MERCHANT sur son magasin doit réussir (201 Created)")
  void shouldAllowCreateProductWithMerchantRole() throws Exception {
    String merchantToken = createToken("MERCHANT", 100L);
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-001", "Baguette", "Desc", new BigDecimal("1.20"), null, 10);

    ProductResponse response =
        new ProductResponse(
            1L, 1L, "REF-001", "Baguette", "Desc", new BigDecimal("1.20"), null, 10, Instant.now(), Instant.now());
    when(productService.createProduct(eq(1L), any(), any(), eq(100L), eq("MERCHANT")))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/v1/stores/1/products")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1L))
        .andExpect(jsonPath("$.name").value("Baguette"));
  }

  @Test
  @DisplayName("POST /v1/stores/{storeId}/products avec rôle ADMIN doit réussir sur n'importe quel magasin")
  void shouldAllowCreateProductWithAdminRole() throws Exception {
    String adminToken = createToken("ADMIN", 1L);
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-ADMIN-01", "Boutique", "Desc", new BigDecimal("2.50"), null, 5);

    ProductResponse response =
        new ProductResponse(
            2L, 1L, "REF-ADMIN-01", "Boutique", "Desc", new BigDecimal("2.50"), null, 5, Instant.now(), Instant.now());
    when(productService.createProduct(eq(1L), any(), any(), eq(1L), eq("ADMIN")))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/v1/stores/1/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(2L));
  }

  @Test
  @DisplayName("PUT /v1/stores/{storeId}/products/{productId} sans token doit renvoyer 401 Unauthorized")
  void shouldDenyUpdateProductWithoutToken() throws Exception {
    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-01", "Nouveau nom", "Desc", new BigDecimal("2.00"), null, 10);

    mockMvc
        .perform(
            put("/v1/stores/1/products/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PUT /v1/stores/{storeId}/products/{productId} avec rôle CLIENT doit renvoyer 403 Forbidden")
  void shouldDenyUpdateProductWithClientRole() throws Exception {
    String clientToken = createToken("CLIENT", 50L);
    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-01", "Nom", "Desc", new BigDecimal("2.00"), null, 10);

    mockMvc
        .perform(
            put("/v1/stores/1/products/10")
                .header("Authorization", "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PUT /v1/stores/{storeId}/products/{productId} avec rôle MERCHANT sur son magasin doit réussir (200 OK)")
  void shouldAllowUpdateProductWithMerchantRole() throws Exception {
    String merchantToken = createToken("MERCHANT", 100L);
    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-01", "Nom Modifié", "Desc", new BigDecimal("2.50"), null, 15);

    ProductResponse response =
        new ProductResponse(
            10L, 1L, "REF-01", "Nom Modifié", "Desc", new BigDecimal("2.50"), null, 15, Instant.now(), Instant.now());
    when(productService.updateProduct(eq(1L), eq(10L), any(), any(), eq(100L), eq("MERCHANT")))
        .thenReturn(response);

    mockMvc
        .perform(
            put("/v1/stores/1/products/10")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Nom Modifié"));
  }

  @Test
  @DisplayName("DELETE /v1/stores/{storeId}/products/{productId} sans token doit renvoyer 401 Unauthorized")
  void shouldDenyDeleteProductWithoutToken() throws Exception {
    mockMvc
        .perform(delete("/v1/stores/1/products/10"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("DELETE /v1/stores/{storeId}/products/{productId} avec rôle CLIENT doit renvoyer 403 Forbidden")
  void shouldDenyDeleteProductWithClientRole() throws Exception {
    String clientToken = createToken("CLIENT", 20L);

    mockMvc
        .perform(
            delete("/v1/stores/1/products/10")
                .header("Authorization", "Bearer " + clientToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("DELETE /v1/stores/{storeId}/products/{productId} par gérant non propriétaire doit renvoyer 403 Forbidden")
  void shouldDenyDeleteProductForNonOwnerMerchant() throws Exception {
    String merchantToken = createToken("MERCHANT", 999L);
    doThrow(new AccessDeniedException("Accès refusé"))
        .when(productService)
        .deleteProduct(eq(1L), eq(10L), eq(999L), eq("MERCHANT"));

    mockMvc
        .perform(
            delete("/v1/stores/1/products/10")
                .header("Authorization", "Bearer " + merchantToken))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("DELETE /v1/stores/{storeId}/products/{productId} par gérant commerçant propriétaire doit renvoyer 204 No Content")
  void shouldAllowMerchantToDeleteProduct() throws Exception {
    String merchantToken = createToken("MERCHANT", 100L);

    mockMvc
        .perform(
            delete("/v1/stores/1/products/10")
                .header("Authorization", "Bearer " + merchantToken))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("PATCH /v1/stores/{storeId}/products/{productId}/stock sans token doit renvoyer 401 Unauthorized")
  void shouldDenyPatchStockWithoutToken() throws Exception {
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-1);

    mockMvc
        .perform(
            patch("/v1/stores/1/products/10/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PATCH /v1/stores/{storeId}/products/{productId}/stock avec token expiré doit renvoyer 401 Unauthorized")
  void shouldDenyPatchStockWithExpiredToken() throws Exception {
    // Token expiré il y a 10 secondes
    String expiredToken = createToken("SERVICE", null, -10000L);
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-1);

    mockMvc
        .perform(
            patch("/v1/stores/1/products/10/stock")
                .header("Authorization", "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PATCH /v1/stores/{storeId}/products/{productId}/stock avec rôle SERVICE (M2M) doit renvoyer 200 OK")
  void shouldAllowServiceRoleToPatchStock() throws Exception {
    String serviceToken = createToken("SERVICE", null);
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-2);

    ProductResponse response =
        new ProductResponse(
            10L, 1L, "REF-001", "Croissant", "Desc", new BigDecimal("1.50"), null, 18, Instant.now(), Instant.now());
    when(productService.updateStock(eq(1L), eq(10L), any(), any(), eq("SERVICE")))
        .thenReturn(response);

    mockMvc
        .perform(
            patch("/v1/stores/1/products/10/stock")
                .header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(18));
  }

  @Test
  @DisplayName("PATCH /v1/stores/{storeId}/products/{productId}/stock avec rôle MERCHANT sur son magasin doit réussir")
  void shouldAllowMerchantToPatchStockOnOwnedStore() throws Exception {
    String merchantToken = createToken("MERCHANT", 100L);
    StockUpdateRequest request = StockUpdateRequest.ofAbsolute(30);

    ProductResponse response =
        new ProductResponse(
            10L, 1L, "REF-001", "Croissant", "Desc", new BigDecimal("1.50"), null, 30, Instant.now(), Instant.now());
    when(productService.updateStock(eq(1L), eq(10L), any(), eq(100L), eq("MERCHANT")))
        .thenReturn(response);

    mockMvc
        .perform(
            patch("/v1/stores/1/products/10/stock")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(30));
  }

  @Test
  @DisplayName("PATCH /v1/stores/{storeId}/products/{productId}/stock avec rôle CLIENT doit être refusé (403 Forbidden)")
  void shouldDenyClientFromPatchingStock() throws Exception {
    String clientToken = createToken("CLIENT", 50L);
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-1);

    mockMvc
        .perform(
            patch("/v1/stores/1/products/10/stock")
                .header("Authorization", "Bearer " + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }
}
