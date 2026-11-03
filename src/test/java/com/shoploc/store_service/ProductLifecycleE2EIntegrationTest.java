package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.exception.GlobalExceptionHandler;
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

    String pubKeyBase64 =
        Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
    ReflectionTestUtils.setField(jwtValidatorService, "publicKeyString", pubKeyBase64);
    jwtValidatorService.init();

    RoleInterceptor roleInterceptor = new RoleInterceptor(jwtValidatorService);

    mockMvc =
        MockMvcBuilders.standaloneSetup(productController)
            .addInterceptors(roleInterceptor)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    store =
        storeRepository.save(
            new Store(
                "Pâtisserie Parisienne",
                "15 boulevard Saint-Germain, Paris",
                "https://cdn.shoploc.fr/patisserie.jpg",
                "Boulangerie-Pâtisserie",
                "FR7630006000011234567890189",
                100L));

    merchantToken = createToken("MERCHANT", 100L);
    clientToken = createToken("CLIENT", 200L);
    serviceToken = createToken("SERVICE", null);
  }

  private String createToken(String role, Long dbId) {
    return Jwts.builder()
        .header()
        .keyId("test-key")
        .and()
        .id(UUID.randomUUID().toString())
        .subject(UUID.randomUUID().toString())
        .issuer("shoploc-auth-service")
        .expiration(new Date(System.currentTimeMillis() + 3600000))
        .claim("role", role)
        .claim("dbId", dbId)
        .claim("userId", UUID.randomUUID().toString())
        .claim("email", role.toLowerCase() + "@shoploc.fr")
        .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  @Test
  @DisplayName("Scénario E2E complet du cycle de vie d'un produit Click & Collect (Création -> Consultation -> Vente -> Rupture -> Réappro -> MAJ -> Suppression)")
  void shouldExecuteCompleteProductLifecycle() throws Exception {
    // 1. Le commerçant crée un produit avec un stock initial de 10
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
                post("/v1/stores/" + store.getId() + "/products")
                    .header("Authorization", "Bearer " + merchantToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.reference").value("REF-E2E-ECLAIR"))
            .andExpect(jsonPath("$.stockQuantity").value(10))
            .andReturn();

    JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
    Long productId = createdJson.get("id").asLong();

    // 2. Le client consulte la vitrine publique du magasin (sans token)
    mockMvc
        .perform(get("/v1/stores/" + store.getId() + "/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(productId))
        .andExpect(jsonPath("$[0].stockQuantity").value(10));

    // 3. Le client consulte le détail du produit
    mockMvc
        .perform(
            get("/v1/stores/" + store.getId() + "/products/" + productId)
                .header("Authorization", "Bearer " + clientToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Éclair au Chocolat"))
        .andExpect(jsonPath("$.price").value(3.50));

    // 4. Commande 1 : sales-service décrémente le stock de 3 via PATCH stock (rôle SERVICE)
    StockUpdateRequest order1 = StockUpdateRequest.ofDelta(-3);
    mockMvc
        .perform(
            patch("/v1/stores/" + store.getId() + "/products/" + productId + "/stock")
                .header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(7));

    // 5. Commande 2 : sales-service décrémente les 7 restants -> stock tombe à 0 (Rupture)
    StockUpdateRequest order2 = StockUpdateRequest.ofDelta(-7);
    mockMvc
        .perform(
            patch("/v1/stores/" + store.getId() + "/products/" + productId + "/stock")
                .header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(0));

    // 6. Commande 3 : sales-service tente de commander 1 exemplaire de plus -> Rejet immédiat (400 Bad Request)
    StockUpdateRequest order3 = StockUpdateRequest.ofDelta(-1);
    mockMvc
        .perform(
            patch("/v1/stores/" + store.getId() + "/products/" + productId + "/stock")
                .header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order3)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Stock insuffisant pour le produit " + productId + " (actuel: 0, demandé: -1)"));

    // 7. Le commerçant se fait réapprovisionner de 15 articles (+15)
    StockUpdateRequest restock = StockUpdateRequest.ofDelta(15);
    mockMvc
        .perform(
            patch("/v1/stores/" + store.getId() + "/products/" + productId + "/stock")
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(restock)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(15));

    // 8. Le commerçant met à jour le prix et la description
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
            put("/v1/stores/" + store.getId() + "/products/" + productId)
                .header("Authorization", "Bearer " + merchantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Éclair au Chocolat Valrhona"))
        .andExpect(jsonPath("$.price").value(3.80));

    // 9. Le commerçant supprime l'article du catalogue
    mockMvc
        .perform(
            delete("/v1/stores/" + store.getId() + "/products/" + productId)
                .header("Authorization", "Bearer " + merchantToken))
        .andExpect(status().isNoContent());

    // 10. La consultation de l'article supprimé doit renvoyer 404 Not Found
    mockMvc
        .perform(get("/v1/stores/" + store.getId() + "/products/" + productId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));

    // Et le catalogue du magasin doit être vide
    mockMvc
        .perform(get("/v1/stores/" + store.getId() + "/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
