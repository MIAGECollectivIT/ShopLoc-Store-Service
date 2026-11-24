package com.shoploc.store_service.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.controller.ProductController;
import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.service.ProductService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProductValidationExceptionHandlerTest {

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private ProductService productService;

  @InjectMocks private ProductController productController;

  private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();
  private static final String STORE_UID = "11111111-1111-1111-1111-111111111111";
  private static final String PRODUCT_UID = "22222222-2222-2222-2222-222222222222";

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(productController)
            .setControllerAdvice(exceptionHandler)
            .build();
  }

  @Test
  @DisplayName("POST empty product validation -> should return 400 with detailed fieldErrors map")
  void shouldReturnDetailedFieldErrorsOnEmptyProductPost() throws Exception {
    mockMvc
        .perform(
            post("/v1/stores/" + STORE_UID + "/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
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
      "POST product validation with negative price and stock -> should return 400 with specific messages")
  void shouldReturnDetailedFieldErrorsOnNegativePriceAndStock() throws Exception {
    ProductCreateRequest invalidRequest =
        new ProductCreateRequest("REF-123", "Pomme", "Desc", new BigDecimal("-2.50"), null, -5);

    mockMvc
        .perform(
            post("/v1/stores/" + STORE_UID + "/products")
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
      "PUT product validation with blank reference and name -> should return 400 with fieldErrors")
  void shouldReturnDetailedFieldErrorsOnBlankPutFields() throws Exception {
    ProductUpdateRequest invalidRequest =
        new ProductUpdateRequest("   ", "   ", "Desc", new BigDecimal("-1.00"), null, -2);

    mockMvc
        .perform(
            put("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID)
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
      "PATCH stock validation with negative absolute quantity -> should return 400 with fieldErrors.newQuantity")
  void shouldReturnFieldErrorOnNegativeAbsoluteStock() throws Exception {
    StockUpdateRequest invalidStock = StockUpdateRequest.ofAbsolute(-10);

    mockMvc
        .perform(
            patch("/v1/stores/" + STORE_UID + "/products/" + PRODUCT_UID + "/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidStock)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(
            jsonPath("$.fieldErrors.newQuantity")
                .value("La nouvelle quantité doit être supérieure ou égale à 0"));
  }

  @Test
  @DisplayName("GET product with non-UUID path variable -> should return 400 Bad Request")
  void shouldReturn400WhenProductUidIsNotUuid() throws Exception {
    mockMvc
        .perform(get("/v1/stores/" + STORE_UID + "/products/10"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"));

    mockMvc
        .perform(get("/v1/stores/123/products"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"));
  }

  @Test
  @DisplayName(
      "GlobalExceptionHandler: direct verification of catalog domain exception responses (ProblemDetail RFC 7807)")
  void shouldVerifyDirectHandlerResponses() {
    UUID productUid = UUID.fromString(PRODUCT_UID);
    ProblemDetail notFoundResp =
        exceptionHandler.handleNotFoundExceptions(new ProductNotFoundException(productUid));
    assertThat(notFoundResp.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(notFoundResp.getDetail())
        .isEqualTo("Produit " + productUid + " introuvable dans le commerce");

    ProblemDetail conflictResp =
        exceptionHandler.handleProductReferenceConflict(
            new ProductReferenceAlreadyExistsException("REF-01"));
    assertThat(conflictResp.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(conflictResp.getDetail()).contains("REF-01");

    ProblemDetail stockResp =
        exceptionHandler.handleInsufficientStockException(
            new InsufficientStockException("Stock insuffisant"));
    assertThat(stockResp.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(stockResp.getProperties().get("error")).isEqualTo("Bad Request");

    ProblemDetail invalidFileResp =
        exceptionHandler.handleInvalidFileException(
            new InvalidFileException("Format non supporté"));
    assertThat(invalidFileResp.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

    ProblemDetail fileStorageResp =
        exceptionHandler.handleFileStorageException(
            new FileStorageException("MinIO S3 down", new RuntimeException("S3 unreachable")));
    assertThat(fileStorageResp.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());

    ProblemDetail accessDeniedResp =
        exceptionHandler.handleAccessDeniedException(new AccessDeniedException("Accès refusé"));
    assertThat(accessDeniedResp.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());

    ProblemDetail unauthorizedResp =
        exceptionHandler.handleUnauthorizedException(new UnauthorizedException("Token absent"));
    assertThat(unauthorizedResp.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }
}
