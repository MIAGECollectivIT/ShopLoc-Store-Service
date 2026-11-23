package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductResponse;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.exception.FileStorageException;
import com.shoploc.store_service.exception.InsufficientStockException;
import com.shoploc.store_service.exception.InvalidFileException;
import com.shoploc.store_service.exception.ProductImageNotFoundException;
import com.shoploc.store_service.exception.ProductReferenceAlreadyExistsException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.security.RoleInterceptor;
import com.shoploc.store_service.storage.ImageResource;
import com.shoploc.store_service.storage.ImageStorageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProductControllerTest {

  @Autowired private ProductController productController;

  @Autowired private StoreRepository storeRepository;

  @Autowired private ProductRepository productRepository;

  @MockitoBean private ImageStorageService imageStorageService;

  private Store store;
  private UUID ownerUid;
  private UUID storeUid;

  @BeforeEach
  void setUp() {
    ownerUid = UUID.randomUUID();
    store =
        storeRepository.save(
            new Store(
                "Epicerie Fine",
                "5 rue Nationale, Lille",
                "https://cdn.shoploc.fr/epicerie.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                ownerUid));
    storeUid = store.getUid();
  }

  private MockHttpServletRequest createMockRequest(String role, UUID userId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(RoleInterceptor.ATTR_USER_ROLE, role);
    request.setAttribute(RoleInterceptor.ATTR_USER_ID, userId);
    return request;
  }

  @Test
  @DisplayName("GET /v1/stores/{storeUid}/products - Should return products list")
  void shouldReturnProductsList() {
    productRepository.save(
        new Product(
            store.getId(), "REF-A", "Pommes Gala", "Bio", new BigDecimal("2.99"), null, 50));
    productRepository.save(
        new Product(
            store.getId(), "REF-B", "Poires Williams", "Bio", new BigDecimal("3.49"), null, 30));

    ResponseEntity<List<ProductResponse>> response = productController.getProducts(storeUid);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(2);
    assertThat(response.getBody())
        .extracting(ProductResponse::getName)
        .containsExactlyInAnyOrder("Pommes Gala", "Poires Williams");
    assertThat(response.getBody())
        .allSatisfy(
            p -> {
              assertThat(p.getUid()).isNotNull();
              assertThat(p.getStoreUid()).isEqualTo(storeUid);
            });
  }

  @Test
  @DisplayName("GET /v1/stores/{storeUid}/products/{productUid} - Should return single product")
  void shouldReturnSingleProduct() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(),
                "REF-JUS",
                "Jus d'orange",
                "Pur jus",
                new BigDecimal("2.10"),
                null,
                10));

    ResponseEntity<ProductResponse> response =
        productController.getProduct(storeUid, product.getUid());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getUid()).isEqualTo(product.getUid());
    assertThat(response.getBody().getStoreUid()).isEqualTo(storeUid);
    assertThat(response.getBody().getName()).isEqualTo("Jus d'orange");
  }

  @Test
  @DisplayName("GET on non-existent store - Should throw StoreNotFoundException")
  void shouldThrowWhenStoreNotFound() {
    UUID nonExistentUid = UUID.randomUUID();
    assertThatThrownBy(() -> productController.getProducts(nonExistentUid))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  @DisplayName(
      "POST /v1/stores/{storeUid}/products (JSON) - Should create product with initial stock")
  void shouldCreateProductJson() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-MIEL", "Miel de lavande", "Pot 500g", new BigDecimal("8.50"), null, 20);

    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);

    ResponseEntity<ProductResponse> response =
        productController.createProductJson(storeUid, request, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getUid()).isNotNull();
    assertThat(response.getBody().getStoreUid()).isEqualTo(storeUid);
    assertThat(response.getBody().getName()).isEqualTo("Miel de lavande");
    assertThat(response.getBody().getStockQuantity()).isEqualTo(20);
    assertThat(response.getHeaders().getLocation().toString())
        .isEqualTo("/v1/stores/" + storeUid + "/products/" + response.getBody().getUid());
  }

  @Test
  @DisplayName("POST /v1/stores/{storeUid}/products (Multipart) - Should upload image to S3")
  void shouldCreateProductMultipartWithImage() {
    when(imageStorageService.uploadProductImage(any()))
        .thenReturn("https://minio.shoploc.fr/products/miel.png");

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-CONFITURE", "Confiture fraise", "Artisanale", new BigDecimal("4.20"), null, 15);

    MockMultipartFile image =
        new MockMultipartFile("image", "fraise.png", "image/png", new byte[] {1, 2, 3});
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);

    ResponseEntity<ProductResponse> response =
        productController.createProduct(storeUid, request, image, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getImageUrl())
        .isEqualTo("https://minio.shoploc.fr/products/miel.png");
  }

  @Test
  @DisplayName(
      "POST with duplicate reference - Should throw ProductReferenceAlreadyExistsException")
  void shouldThrowWhenDuplicateReference() {
    productRepository.save(
        new Product(
            store.getId(), "REF-DOUBLE", "Article", "Desc", new BigDecimal("1.00"), null, 5));

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-DOUBLE", "Autre article", "Desc", new BigDecimal("2.00"), null, 10);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);

    assertThatThrownBy(() -> productController.createProductJson(storeUid, request, servletRequest))
        .isInstanceOf(ProductReferenceAlreadyExistsException.class);
  }

  @Test
  @DisplayName("PUT /v1/stores/{storeUid}/products/{productUid} - Should update product")
  void shouldUpdateProduct() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-OLD", "Ancien nom", "Desc", new BigDecimal("3.00"), null, 10));

    ProductUpdateRequest updateReq =
        new ProductUpdateRequest(
            "REF-OLD", "Nouveau nom", "Description mise à jour", new BigDecimal("3.50"), null, 12);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);

    ResponseEntity<ProductResponse> response =
        productController.updateProductJson(storeUid, product.getUid(), updateReq, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getName()).isEqualTo("Nouveau nom");
    assertThat(response.getBody().getPrice()).isEqualByComparingTo("3.50");
    assertThat(response.getBody().getStockQuantity()).isEqualTo(12);
  }

  @Test
  @DisplayName(
      "DELETE /v1/stores/{storeUid}/products/{productUid} - Should delete item (204 No Content)")
  void shouldDeleteProduct() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-DEL", "A supprimer", "Desc", new BigDecimal("1.00"), null, 5));

    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);

    ResponseEntity<Void> response =
        productController.deleteProduct(storeUid, product.getUid(), servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(productRepository.findByUid(product.getUid())).isEmpty();
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeUid}/products/{productUid}/stock - Should update stock (delta)")
  void shouldUpdateStockWithDelta() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-STK", "Biscuit", "Desc", new BigDecimal("2.00"), null, 20));

    StockUpdateRequest stockReq = StockUpdateRequest.ofDelta(-5);
    MockHttpServletRequest servletRequest = createMockRequest("SERVICE", null);

    ResponseEntity<ProductResponse> response =
        productController.updateStock(storeUid, product.getUid(), stockReq, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStockQuantity()).isEqualTo(15);
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeUid}/products/{productUid}/stock - Should update stock (absolute value)")
  void shouldUpdateStockWithAbsoluteValue() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-STK2", "Chocolat", "Desc", new BigDecimal("3.00"), null, 10));

    StockUpdateRequest stockReq = StockUpdateRequest.ofAbsolute(45);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);

    ResponseEntity<ProductResponse> response =
        productController.updateStock(storeUid, product.getUid(), stockReq, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getStockQuantity()).isEqualTo(45);
  }

  @Test
  @DisplayName("PATCH stock below available inventory - Should throw InsufficientStockException")
  void shouldThrowWhenStockGoesBelowZero() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-RUPTURE", "Café", "Desc", new BigDecimal("5.00"), null, 3));

    StockUpdateRequest stockReq = StockUpdateRequest.ofDelta(-5);
    MockHttpServletRequest servletRequest = createMockRequest("SERVICE", null);

    assertThatThrownBy(
            () ->
                productController.updateStock(storeUid, product.getUid(), stockReq, servletRequest))
        .isInstanceOf(InsufficientStockException.class);
  }

  @Test
  @DisplayName(
      "Product creation with unsupported file format (PDF) - Should throw InvalidFileException")
  void shouldRejectUnsupportedFileFormatOnProductCreation() {
    MockMultipartFile invalidFile =
        new MockMultipartFile("image", "facture.pdf", "application/pdf", new byte[] {1, 2, 3});
    when(imageStorageService.uploadProductImage(any()))
        .thenThrow(new InvalidFileException("Format de fichier non supporté (application/pdf)"));

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-PDF", "Produit PDF", "Desc", new BigDecimal("10.00"), null, 5);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);

    assertThatThrownBy(
            () -> productController.createProduct(storeUid, request, invalidFile, servletRequest))
        .isInstanceOf(InvalidFileException.class)
        .hasMessageContaining("non supporté");
  }

  @Test
  @DisplayName("Product creation with S3 MinIO storage failure - Should throw FileStorageException")
  void shouldThrowFileStorageExceptionWhenS3UnavailableOnCreation() {
    MockMultipartFile validImage =
        new MockMultipartFile("image", "photo.png", "image/png", new byte[] {1, 2, 3});
    when(imageStorageService.uploadProductImage(any()))
        .thenThrow(
            new FileStorageException(
                "Échec de l'upload de l'image sur le stockage S3 MinIO",
                new RuntimeException("Connection refused")));

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-S3-DOWN", "Produit S3 Down", "Desc", new BigDecimal("10.00"), null, 5);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);

    assertThatThrownBy(
            () -> productController.createProduct(storeUid, request, validImage, servletRequest))
        .isInstanceOf(FileStorageException.class)
        .hasMessageContaining("MinIO");
  }

  @Test
  @DisplayName("Product update with S3 MinIO storage failure - Should throw FileStorageException")
  void shouldThrowFileStorageExceptionWhenS3UnavailableOnUpdate() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-UPD-S3", "Article", "Desc", new BigDecimal("5.00"), null, 3));

    MockMultipartFile validImage =
        new MockMultipartFile("image", "new-photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(imageStorageService.uploadProductImage(any()))
        .thenThrow(
            new FileStorageException(
                "Échec de l'upload de l'image sur le stockage S3 MinIO",
                new RuntimeException("S3 Timeout")));

    ProductUpdateRequest request =
        new ProductUpdateRequest(
            "REF-UPD-S3", "Article Modifié", "Desc", new BigDecimal("6.00"), null, 3);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);

    assertThatThrownBy(
            () ->
                productController.updateProduct(
                    storeUid, product.getUid(), request, validImage, servletRequest))
        .isInstanceOf(FileStorageException.class)
        .hasMessageContaining("MinIO");
  }

  @Test
  @DisplayName("Product image streaming - Should return bytes with correct Content-Type")
  void shouldGetProductImageSuccessfully() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(),
                "REF-IMG-STREAM",
                "Article Visuel",
                "Desc",
                new BigDecimal("5.00"),
                "https://cdn.shoploc.fr/product-images/stream.png",
                3));

    byte[] expectedBytes = new byte[] {0x12, 0x34, 0x56};
    when(imageStorageService.downloadImage("https://cdn.shoploc.fr/product-images/stream.png"))
        .thenReturn(Optional.of(new ImageResource(expectedBytes, "image/png")));

    ResponseEntity<byte[]> response = productController.getProductImage(storeUid, product.getUid());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedBytes);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
    assertThat(response.getHeaders().getCacheControl()).contains("max-age=86400");
  }

  @Test
  @DisplayName("Product image streaming when absent - Should throw ProductImageNotFoundException")
  void shouldThrowWhenProductHasNoImage() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(),
                "REF-NO-IMG",
                "Article Sans Visuel",
                "Desc",
                new BigDecimal("5.00"),
                null,
                3));

    assertThatThrownBy(() -> productController.getProductImage(storeUid, product.getUid()))
        .isInstanceOf(ProductImageNotFoundException.class);
  }

  @Test
  @DisplayName("Multipart product image patch - Upload and database persistence")
  void shouldPatchProductImageMultipart() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(),
                "REF-PATCH-IMG",
                "Article A",
                "Desc",
                new BigDecimal("5.00"),
                null,
                3));

    MockMultipartFile imageFile =
        new MockMultipartFile("image", "new-art.png", "image/png", new byte[] {1, 2, 3});
    when(imageStorageService.uploadProductImage(any()))
        .thenReturn("https://cdn.shoploc.fr/product-images/new-art.png");

    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);
    ResponseEntity<ProductResponse> response =
        productController.updateProductImageMultipart(
            storeUid, product.getUid(), imageFile, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getImageUrl())
        .isEqualTo("https://cdn.shoploc.fr/product-images/new-art.png");

    Product updated = productRepository.findByUid(product.getUid()).orElseThrow();
    assertThat(updated.getImageUrl())
        .isEqualTo("https://cdn.shoploc.fr/product-images/new-art.png");
  }

  @Test
  @DisplayName("JSON product image patch - Direct URL update")
  void shouldPatchProductImageJson() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(),
                "REF-PATCH-JSON",
                "Article B",
                "Desc",
                new BigDecimal("5.00"),
                null,
                3));

    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", ownerUid);
    ResponseEntity<ProductResponse> response =
        productController.updateProductImageJson(
            storeUid,
            product.getUid(),
            Map.of("imageUrl", "https://cdn.shoploc.fr/product-images/custom.jpg"),
            servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getImageUrl())
        .isEqualTo("https://cdn.shoploc.fr/product-images/custom.jpg");

    Product updated = productRepository.findByUid(product.getUid()).orElseThrow();
    assertThat(updated.getImageUrl()).isEqualTo("https://cdn.shoploc.fr/product-images/custom.jpg");
  }
}
