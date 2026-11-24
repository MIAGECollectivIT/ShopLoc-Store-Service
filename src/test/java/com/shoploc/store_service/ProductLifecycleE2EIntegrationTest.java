package com.shoploc.store_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.controller.ProductController;
import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.entity.Store;
import com.shoploc.store_service.exception.GlobalExceptionHandler;
import com.shoploc.store_service.repository.ProductRepository;
import com.shoploc.store_service.repository.StoreRepository;
import com.shoploc.store_service.security.JwtValidatorService;
import com.shoploc.store_service.security.RoleInterceptor;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
@ActiveProfiles("test")
class ProductLifecycleE2EIntegrationTest {

  private static KeyPair testKeyPair;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private ProductController productController;
  @Autowired private StoreRepository storeRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private JwtValidatorService jwtValidatorService;

  private Store store;
  private UUID merchantUserUid;
  private String merchantToken;
  private String clientToken;
  private String serviceToken;

  @BeforeAll
  static void initKeys() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    testKeyPair = kpg.generateKeyPair();
  }

  @BeforeEach
  void setUp() {
    productRepository.deleteAll();
    storeRepository.deleteAll();

    String pubKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
    ReflectionTestUtils.setField(jwtValidatorService, "publicKeyString", pubKeyBase64);
    jwtValidatorService.init();

    RoleInterceptor roleInterceptor = new RoleInterceptor(jwtValidatorService);

    mockMvc =
        MockMvcBuilders.standaloneSetup(productController)
            .addInterceptors(roleInterceptor)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    merchantUserUid = UUID.randomUUID();
    store =
        storeRepository.save(
            new Store(
                "Pâtisserie Parisienne",
                "15 boulevard Saint-Germain, Paris",
                "https://cdn.shoploc.fr/patisserie.jpg",
                "Boulangerie-Pâtisserie",
                "FR7630006000011234567890189",
                merchantUserUid));

    merchantToken = createToken("MERCHANT", merchantUserUid);
    clientToken = createToken("CLIENT", UUID.randomUUID());
    serviceToken = createToken("SERVICE", null);
  }

  private String createToken(String role, UUID userId) {
    var builder =
        Jwts.builder()
            .header()
            .keyId("test-key")
            .and()
            .id(UUID.randomUUID().toString())
            .subject(userId != null ? userId.toString() : "service-account")
            .issuer("shoploc-auth-service")
            .expiration(new Date(System.currentTimeMillis() + 3600000))
            .claim("role", role)
            .claim("email", role.toLowerCase() + "@shoploc.fr");
    if (userId != null) {
      builder.claim("userId", userId.toString());
    }
    return builder.signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
  }

  @Test
  @DisplayName(
      "Complete E2E lifecycle scenario of a Click & Collect product (Creation -> Consultation -> Sale -> Out of stock -> Restock -> Update -> Deletion)")
  void shouldExecuteCompleteProductLifecycle() throws Exception {
    // 1. The merchant creates a product with an initial stock of 10
    ProductCreateRequest createRequest =
        new ProductCreateRequest(
            "REF-E2E-ECLAIR",
            "Éclair au Chocolat",
            "Pâte à choux pur beurre et ganache grand cru",
            new BigDecimal("3.50"),
            "https://cdn.shoploc.fr/eclair.jpg",
            10);

    MvcResult createResult =
        mockMvc
            .perform(
                post("/v1/stores/" + store.getUid() + "/products")
                    .header("Authorization", "Bearer " + merchantToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.uid").exists())
            .andExpect(jsonPath("$.storeUid").value(store.getUid().toString()))
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.storeId").doesNotExist())
            .andExpect(jsonPath("$.reference").value("REF-E2E-ECLAIR"))
            .andExpect(jsonPath("$.stockQuantity").value(10))
            .andReturn();

    JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
    String productUid = createdJson.get("uid").asText();

    // 2. The client browses the public storefront (without token)
    mockMvc
        .perform(get("/v1/stores/" + store.getUid() + "/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].uid").value(productUid))
        .andExpect(jsonPath("$[0].storeUid").value(store.getUid().toString()))
        .andExpect(jsonPath("$[0].stockQuantity").value(10));

    // 3. The client views product details
    mockMvc
        .perform(
            get("/v1/stores/" + store.getUid() + "/products/" + productUid)
                .header("Authorization", "Bearer " + clientToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Éclair au Chocolat"))
        .andExpect(jsonPath("$.price").value(3.50));

    // 4. Order 1: sales-service decrements stock by 3 via PATCH stock (SERVICE role)
    StockUpdateRequest order1 = StockUpdateRequest.ofDelta(-3);
    mockMvc
        .perform(
            patch("/v1/stores/" + store.getUid() + "/products/" + productUid + "/stock")
                .header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(7));

    // 5. Order 2: sales-service decrements the remaining 7 -> stock drops to 0 (Out of stock)
    StockUpdateRequest order2 = StockUpdateRequest.ofDelta(-7);
    mockMvc
        .perform(
            patch("/v1/stores/" + store.getUid() + "/products/" + productUid + "/stock")
                .header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(0));

    // 6. Order 3: sales-service attempts to order 1 more item -> Immediate rejection (400 Bad
    // Request)
    StockUpdateRequest order3 = StockUpdateRequest.ofDelta(-1);
    mockMvc
        .perform(
            patch("/v1/stores/" + store.getUid() + "/products/" + productUid + "/stock")
                .header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order3)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Stock insuffisant pour le produit "
                        + productUid
                        + " (actuel: 0, demandé: -1)"));

    // 7. The merchant restocks 15 items (+15)
    StockUpdateRequest restock = StockUpdateRequest.ofDelta(15);
    mockMvc
        .perform(
            patch("/v1/stores/" + store.getUid() + "/products/" + productUid + "/stock")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(restock)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(15));

    // 8. The merchant updates price and description
    ProductUpdateRequest updateReq =
        new ProductUpdateRequest(
            "REF-E2E-ECLAIR",
            "Éclair au Chocolat Valrhona",
            "Pâte à choux pur beurre et ganache grand cru Valrhona 70%",
            new BigDecimal("3.80"),
            "https://cdn.shoploc.fr/eclair-valrhona.jpg",
            15);

    mockMvc
        .perform(
            put("/v1/stores/" + store.getUid() + "/products/" + productUid)
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Éclair au Chocolat Valrhona"))
        .andExpect(jsonPath("$.price").value(3.80));

    // 9. The merchant deletes the item from the catalog
    mockMvc
        .perform(
            delete("/v1/stores/" + store.getUid() + "/products/" + productUid)
                .header("Authorization", "Bearer " + merchantToken))
        .andExpect(status().isNoContent());

    // 10. Accessing the deleted item must return 404 Not Found
    mockMvc
        .perform(get("/v1/stores/" + store.getUid() + "/products/" + productUid))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));

    // And the store catalog must be empty
    mockMvc
        .perform(get("/v1/stores/" + store.getUid() + "/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
