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
import com.shoploc.store_service.storage.ImageResource;
import com.shoploc.store_service.storage.ImageStorageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  @BeforeEach
  void setUp() {
    store =
        storeRepository.save(
            new Store(
                "Epicerie Fine",
                "5 rue Nationale, Lille",
                "https://cdn.shoploc.fr/epicerie.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                100L));
  }

  private MockHttpServletRequest createMockRequest(String role, Long dbId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute("authenticatedUserRole", role);
    request.setAttribute("authenticatedUserDbId", dbId);
    return request;
  }

  @Test
  @DisplayName("GET /v1/stores/{storeId}/products - Devrait retourner la liste des produits")
  void shouldReturnProductsList() {
    productRepository.save(
        new Product(
            store.getId(), "REF-A", "Pommes Gala", "Bio", new BigDecimal("2.99"), null, 50));
    productRepository.save(
        new Product(
            store.getId(), "REF-B", "Poires Williams", "Bio", new BigDecimal("3.49"), null, 30));

    ResponseEntity<List<ProductResponse>> response = productController.getProducts(store.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(2);
    assertThat(response.getBody())
        .extracting(ProductResponse::getName)
        .containsExactlyInAnyOrder("Pommes Gala", "Poires Williams");
  }

  @Test
  @DisplayName(
      "GET /v1/stores/{storeId}/products/{productId} - Devrait retourner le produit unitaire")
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
        productController.getProduct(store.getId(), product.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getName()).isEqualTo("Jus d'orange");
  }

  @Test
  @DisplayName("GET sur magasin inexistant - Devrait lever StoreNotFoundException")
  void shouldThrowWhenStoreNotFound() {
    assertThatThrownBy(() -> productController.getProducts(9999L))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  @DisplayName(
      "POST /v1/stores/{storeId}/products (JSON) - Devrait créer un produit avec stock initial")
  void shouldCreateProductJson() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-MIEL", "Miel de lavande", "Pot 500g", new BigDecimal("8.50"), null, 20);

    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);

    ResponseEntity<ProductResponse> response =
        productController.createProductJson(store.getId(), request, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getId()).isNotNull();
    assertThat(response.getBody().getName()).isEqualTo("Miel de lavande");
    assertThat(response.getBody().getStockQuantity()).isEqualTo(20);
  }

  @Test
  @DisplayName("POST /v1/stores/{storeId}/products (Multipart) - Devrait uploader l'image vers S3")
  void shouldCreateProductMultipartWithImage() {
    when(imageStorageService.uploadProductImage(any()))
        .thenReturn("https://minio.shoploc.fr/products/miel.png");

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-CONFITURE", "Confiture fraise", "Artisanale", new BigDecimal("4.20"), null, 15);

    MockMultipartFile image =
        new MockMultipartFile("image", "fraise.png", "image/png", new byte[] {1, 2, 3});
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);

    ResponseEntity<ProductResponse> response =
        productController.createProduct(store.getId(), request, image, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getImageUrl())
        .isEqualTo("https://minio.shoploc.fr/products/miel.png");
  }

  @Test
  @DisplayName(
      "POST avec référence en doublon - Devrait lever ProductReferenceAlreadyExistsException")
  void shouldThrowWhenDuplicateReference() {
    productRepository.save(
        new Product(
            store.getId(), "REF-DOUBLE", "Article", "Desc", new BigDecimal("1.00"), null, 5));

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-DOUBLE", "Autre article", "Desc", new BigDecimal("2.00"), null, 10);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);

    assertThatThrownBy(
            () -> productController.createProductJson(store.getId(), request, servletRequest))
        .isInstanceOf(ProductReferenceAlreadyExistsException.class);
  }

  @Test
  @DisplayName("PUT /v1/stores/{storeId}/products/{productId} - Devrait mettre à jour le produit")
  void shouldUpdateProduct() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-OLD", "Ancien nom", "Desc", new BigDecimal("3.00"), null, 10));

    ProductUpdateRequest updateReq =
        new ProductUpdateRequest(
            "REF-OLD", "Nouveau nom", "Description mise à jour", new BigDecimal("3.50"), null, 12);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);

    ResponseEntity<ProductResponse> response =
        productController.updateProductJson(
            store.getId(), product.getId(), updateReq, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getName()).isEqualTo("Nouveau nom");
    assertThat(response.getBody().getPrice()).isEqualByComparingTo("3.50");
    assertThat(response.getBody().getStockQuantity()).isEqualTo(12);
  }

  @Test
  @DisplayName(
      "DELETE /v1/stores/{storeId}/products/{productId} - Devrait supprimer l'article (204 No Content)")
  void shouldDeleteProduct() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-DEL", "A supprimer", "Desc", new BigDecimal("1.00"), null, 5));

    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);

    ResponseEntity<Void> response =
        productController.deleteProduct(store.getId(), product.getId(), servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(productRepository.findById(product.getId())).isEmpty();
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeId}/products/{productId}/stock - Devrait ajuster le stock (delta)")
  void shouldUpdateStockWithDelta() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-STK", "Biscuit", "Desc", new BigDecimal("2.00"), null, 20));

    // Décrémenter de 5 unités (achat Click & Collect)
    StockUpdateRequest stockReq = StockUpdateRequest.ofDelta(-5);
    MockHttpServletRequest servletRequest = createMockRequest("SERVICE", null);

    ResponseEntity<ProductResponse> response =
        productController.updateStock(store.getId(), product.getId(), stockReq, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStockQuantity()).isEqualTo(15);
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeId}/products/{productId}/stock - Devrait ajuster le stock (valeur absolue)")
  void shouldUpdateStockWithAbsoluteValue() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-STK2", "Chocolat", "Desc", new BigDecimal("3.00"), null, 10));

    StockUpdateRequest stockReq = StockUpdateRequest.ofAbsolute(45);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);

    ResponseEntity<ProductResponse> response =
        productController.updateStock(store.getId(), product.getId(), stockReq, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getStockQuantity()).isEqualTo(45);
  }

  @Test
  @DisplayName(
      "PATCH stock au-delà de la réserve disponible - Devrait lever InsufficientStockException")
  void shouldThrowWhenStockGoesBelowZero() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-RUPTURE", "Café", "Desc", new BigDecimal("5.00"), null, 3));

    // Retrait de 5 alors qu'il n'en reste que 3
    StockUpdateRequest stockReq = StockUpdateRequest.ofDelta(-5);
    MockHttpServletRequest servletRequest = createMockRequest("SERVICE", null);

    assertThatThrownBy(
            () ->
                productController.updateStock(
                    store.getId(), product.getId(), stockReq, servletRequest))
        .isInstanceOf(InsufficientStockException.class);
  }

  // --- TESTS VALIDATION FICHIER & REJET S3 MINIO ---

  @Test
  @DisplayName("Création produit avec fichier non supporté (PDF) - Doit lever InvalidFileException")
  void shouldRejectUnsupportedFileFormatOnProductCreation() {
    MockMultipartFile invalidFile =
        new MockMultipartFile("image", "facture.pdf", "application/pdf", new byte[] {1, 2, 3});
    when(imageStorageService.uploadProductImage(any()))
        .thenThrow(new InvalidFileException("Format de fichier non supporté (application/pdf)"));

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-PDF", "Produit PDF", "Desc", new BigDecimal("10.00"), null, 5);
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);

    assertThatThrownBy(
            () ->
                productController.createProduct(
                    store.getId(), request, invalidFile, servletRequest))
        .isInstanceOf(InvalidFileException.class)
        .hasMessageContaining("non supporté");
  }

  @Test
  @DisplayName("Création produit avec panne stockage S3 MinIO - Doit lever FileStorageException")
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
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);

    assertThatThrownBy(
            () ->
                productController.createProduct(store.getId(), request, validImage, servletRequest))
        .isInstanceOf(FileStorageException.class)
        .hasMessageContaining("MinIO");
  }

  @Test
  @DisplayName("Mise à jour produit avec panne stockage S3 MinIO - Doit lever FileStorageException")
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
    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);

    assertThatThrownBy(
            () ->
                productController.updateProduct(
                    store.getId(), product.getId(), request, validImage, servletRequest))
        .isInstanceOf(FileStorageException.class)
        .hasMessageContaining("MinIO");
  }

  @Test
  @DisplayName("Streaming image produit - Doit renvoyer les octets avec bon Content-Type")
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

    ResponseEntity<byte[]> response =
        productController.getProductImage(store.getId(), product.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedBytes);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
    assertThat(response.getHeaders().getCacheControl()).contains("max-age=86400");
  }

  @Test
  @DisplayName("Streaming image produit absente - Doit lever ProductImageNotFoundException")
  void shouldThrowWhenProductHasNoImage() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-NO-IMG", "Article Sans Visuel", "Desc", new BigDecimal("5.00"), null, 3));

    assertThatThrownBy(() -> productController.getProductImage(store.getId(), product.getId()))
        .isInstanceOf(ProductImageNotFoundException.class);
  }

  @Test
  @DisplayName("Patch image produit multipart - Upload et persistance en base")
  void shouldPatchProductImageMultipart() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-PATCH-IMG", "Article A", "Desc", new BigDecimal("5.00"), null, 3));

    MockMultipartFile imageFile =
        new MockMultipartFile("image", "new-art.png", "image/png", new byte[] {1, 2, 3});
    when(imageStorageService.uploadProductImage(any()))
        .thenReturn("https://cdn.shoploc.fr/product-images/new-art.png");

    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);
    ResponseEntity<ProductResponse> response =
        productController.updateProductImageMultipart(
            store.getId(), product.getId(), imageFile, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getImageUrl())
        .isEqualTo("https://cdn.shoploc.fr/product-images/new-art.png");

    Product updated = productRepository.findById(product.getId()).orElseThrow();
    assertThat(updated.getImageUrl()).isEqualTo("https://cdn.shoploc.fr/product-images/new-art.png");
  }

  @Test
  @DisplayName("Patch image produit JSON - Mise à jour par URL directe")
  void shouldPatchProductImageJson() {
    Product product =
        productRepository.save(
            new Product(
                store.getId(), "REF-PATCH-JSON", "Article B", "Desc", new BigDecimal("5.00"), null, 3));

    MockHttpServletRequest servletRequest = createMockRequest("MERCHANT", 100L);
    ResponseEntity<ProductResponse> response =
        productController.updateProductImageJson(
            store.getId(),
            product.getId(),
            Map.of("imageUrl", "https://cdn.shoploc.fr/product-images/custom.jpg"),
            servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getImageUrl())
        .isEqualTo("https://cdn.shoploc.fr/product-images/custom.jpg");

    Product updated = productRepository.findById(product.getId()).orElseThrow();
    assertThat(updated.getImageUrl()).isEqualTo("https://cdn.shoploc.fr/product-images/custom.jpg");
  }
}
