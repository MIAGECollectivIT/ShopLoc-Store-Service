package com.shoploc.store_service.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.ProductController;
import com.shoploc.store_service.ProductService;
import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProductValidationExceptionHandlerTest {

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private ProductService productService;

  @InjectMocks private ProductController productController;

  private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(productController)
            .setControllerAdvice(exceptionHandler)
            .build();
  }

  @Test
  @DisplayName(
      "Validation POST produit vide -> doit renvoyer 400 avec la map fieldErrors détaillée")
  void shouldReturnDetailedFieldErrorsOnEmptyProductPost() throws Exception {
    mockMvc
        .perform(
            post("/v1/stores/1/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("Validation des données d'entrée échouée"))
        .andExpect(
            jsonPath("$.fieldErrors.reference").value("La référence de l'article est obligatoire"))
        .andExpect(jsonPath("$.fieldErrors.name").value("Le nom du produit est obligatoire"))
        .andExpect(jsonPath("$.fieldErrors.price").value("Le prix du produit est obligatoire"));
  }

  @Test
  @DisplayName(
      "Validation POST produit avec prix et stock négatifs -> doit renvoyer 400 avec messages précis")
  void shouldReturnDetailedFieldErrorsOnNegativePriceAndStock() throws Exception {
    ProductCreateRequest invalidRequest =
        new ProductCreateRequest("REF-123", "Pomme", "Desc", new BigDecimal("-2.50"), null, -5);

    mockMvc
        .perform(
            post("/v1/stores/1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.fieldErrors.price").value("Le prix doit être positif ou nul"))
        .andExpect(
            jsonPath("$.fieldErrors.stockQuantity").value("Le stock ne peut pas être négatif"));
  }

  @Test
  @DisplayName(
      "Validation PUT produit avec référence et nom blancs -> doit renvoyer 400 avec fieldErrors")
  void shouldReturnDetailedFieldErrorsOnBlankPutFields() throws Exception {
    ProductUpdateRequest invalidRequest =
        new ProductUpdateRequest("   ", "   ", "Desc", new BigDecimal("-1.00"), null, -2);

    mockMvc
        .perform(
            put("/v1/stores/1/products/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(
            jsonPath("$.fieldErrors.reference").value("La référence de l'article est obligatoire"))
        .andExpect(jsonPath("$.fieldErrors.name").value("Le nom du produit est obligatoire"))
        .andExpect(jsonPath("$.fieldErrors.price").value("Le prix doit être positif ou nul"))
        .andExpect(
            jsonPath("$.fieldErrors.stockQuantity").value("Le stock ne peut pas être négatif"));
  }

  @Test
  @DisplayName(
      "Validation PATCH stock avec quantité absolue négative -> doit renvoyer 400 avec fieldErrors.newQuantity")
  void shouldReturnFieldErrorOnNegativeAbsoluteStock() throws Exception {
    StockUpdateRequest invalidStock = StockUpdateRequest.ofAbsolute(-10);

    mockMvc
        .perform(
            patch("/v1/stores/1/products/10/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidStock)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(
            jsonPath("$.fieldErrors.newQuantity")
                .value("La nouvelle quantité doit être supérieure ou égale à 0"));
  }

  @Test
  @DisplayName(
      "GlobalExceptionHandler : vérification directe des réponses d'exception métier catalogue")
  void shouldVerifyDirectHandlerResponses() {
    ResponseEntity<Map<String, Object>> notFoundResp =
        exceptionHandler.handleNotFoundExceptions(new ProductNotFoundException(1L, 99L));
    assertThat(notFoundResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(notFoundResp.getBody().get("message"))
        .isEqualTo("Produit 99 introuvable dans le commerce 1");

    ResponseEntity<Map<String, Object>> conflictResp =
        exceptionHandler.handleProductReferenceConflict(
            new ProductReferenceAlreadyExistsException("REF-01", 1L));
    assertThat(conflictResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflictResp.getBody().get("message").toString()).contains("REF-01");

    ResponseEntity<Map<String, Object>> stockResp =
        exceptionHandler.handleInsufficientStockException(
            new InsufficientStockException("Stock insuffisant"));
    assertThat(stockResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(stockResp.getBody().get("error")).isEqualTo("Bad Request");

    ResponseEntity<Map<String, Object>> invalidFileResp =
        exceptionHandler.handleInvalidFileException(
            new InvalidFileException("Format non supporté"));
    assertThat(invalidFileResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<Map<String, Object>> fileStorageResp =
        exceptionHandler.handleFileStorageException(
            new FileStorageException("MinIO S3 down", new RuntimeException("S3 unreachable")));
    assertThat(fileStorageResp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

    ResponseEntity<Map<String, Object>> accessDeniedResp =
        exceptionHandler.handleAccessDeniedException(new AccessDeniedException("Accès refusé"));
    assertThat(accessDeniedResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<Map<String, Object>> unauthorizedResp =
        exceptionHandler.handleUnauthorizedException(new UnauthorizedException("Token absent"));
    assertThat(unauthorizedResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
