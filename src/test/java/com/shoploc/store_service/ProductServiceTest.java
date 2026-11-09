package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductResponse;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.exception.AccessDeniedException;
import com.shoploc.store_service.exception.InsufficientStockException;
import com.shoploc.store_service.exception.ProductImageNotFoundException;
import com.shoploc.store_service.exception.ProductNotFoundException;
import com.shoploc.store_service.exception.ProductReferenceAlreadyExistsException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.storage.ImageResource;
import com.shoploc.store_service.storage.ImageStorageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private StoreRepository storeRepository;
  @Mock private ImageStorageService imageStorageService;

  @InjectMocks private ProductService productService;

  private Store sampleStore;
  private Product sampleProduct;

  @BeforeEach
  void setUp() {
    sampleStore =
        new Store(
            "Boulangerie Paul",
            "10 rue Royale, Lille",
            "https://cdn.shoploc.fr/store.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            100L);
    sampleStore.setId(1L);

    sampleProduct =
        new Product(
            1L,
            "REF-001",
            "Croissant au beurre",
            "Croissant pur beurre AOP",
            new BigDecimal("1.50"),
            "https://cdn.shoploc.fr/croissant.jpg",
            20);
    sampleProduct.setId(10L);
  }

  @Test
  @DisplayName("getProductsByStore should return product list for store")
  void shouldReturnProductsForStore() {
    when(storeRepository.existsById(1L)).thenReturn(true);
    when(productRepository.findByStoreId(1L)).thenReturn(List.of(sampleProduct));

    List<ProductResponse> responses = productService.getProductsByStore(1L);
    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).getName()).isEqualTo("Croissant au beurre");
    assertThat(responses.get(0).getReference()).isEqualTo("REF-001");
  }

  @Test
  @DisplayName("getProductsByStore on non-existent store should throw StoreNotFoundException")
  void shouldThrowWhenStoreNotFoundOnGetProducts() {
    when(storeRepository.existsById(999L)).thenReturn(false);

    assertThatThrownBy(() -> productService.getProductsByStore(999L))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  @DisplayName("getProductById should return product when it exists in store")
  void shouldReturnProductById() {
    when(storeRepository.existsById(1L)).thenReturn(true);
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));

    ProductResponse response = productService.getProductById(1L, 10L);
    assertThat(response.getId()).isEqualTo(10L);
    assertThat(response.getName()).isEqualTo("Croissant au beurre");
  }

  @Test
  @DisplayName("getProductById on non-existent product should throw ProductNotFoundException")
  void shouldThrowWhenProductNotFoundById() {
    when(storeRepository.existsById(1L)).thenReturn(true);
    when(productRepository.findByIdAndStoreId(999L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getProductById(1L, 999L))
        .isInstanceOf(ProductNotFoundException.class)
        .hasMessageContaining("999");
  }

  @Test
  @DisplayName("createProduct with MERCHANT role on owned store should succeed")
  void shouldCreateProductForMerchantOwner() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-002")).thenReturn(false);
    when(productRepository.save(any(Product.class)))
        .thenAnswer(
            inv -> {
              Product p = inv.getArgument(0);
              p.setId(20L);
              return p;
            });

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-002", "Pain au chocolat", "Pur beurre", new BigDecimal("1.60"), null, 15);

    ProductResponse response = productService.createProduct(1L, request, null, 100L, "MERCHANT");

    assertThat(response.getId()).isEqualTo(20L);
    assertThat(response.getName()).isEqualTo("Pain au chocolat");
    assertThat(response.getStockQuantity()).isEqualTo(15);
  }

  @Test
  @DisplayName(
      "createProduct with MERCHANT role on another merchant's store should throw AccessDeniedException")
  void shouldDenyCreateProductForNonOwnerMerchant() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-002", "Pain au chocolat", "Pur beurre", new BigDecimal("1.60"), null, 15);

    assertThatThrownBy(() -> productService.createProduct(1L, request, null, 200L, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("vous n'êtes pas le commerçant gestionnaire");
  }

  @Test
  @DisplayName("createProduct with ADMIN role on any store should succeed")
  void shouldAllowAdminToCreateProductOnAnyStore() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-003")).thenReturn(false);
    when(productRepository.save(any(Product.class)))
        .thenAnswer(
            inv -> {
              Product p = inv.getArgument(0);
              p.setId(30L);
              return p;
            });

    ProductCreateRequest request =
        new ProductCreateRequest("REF-003", "Brioche", "Sucre", new BigDecimal("3.00"), null, 5);

    ProductResponse response = productService.createProduct(1L, request, null, 999L, "ADMIN");

    assertThat(response.getId()).isEqualTo(30L);
  }

  @Test
  @DisplayName(
      "createProduct with existing reference in same store should throw ProductReferenceAlreadyExistsException")
  void shouldThrowWhenReferenceAlreadyExistsInStore() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-001")).thenReturn(true);

    ProductCreateRequest request =
        new ProductCreateRequest("REF-001", "Doublon", "Desc", new BigDecimal("1.00"), null, 5);

    assertThatThrownBy(() -> productService.createProduct(1L, request, null, 100L, "MERCHANT"))
        .isInstanceOf(ProductReferenceAlreadyExistsException.class)
        .hasMessageContaining("existe déjà dans le commerce");
  }

  @Test
  @DisplayName("createProduct with multipart image should call imageStorageService")
  void shouldUploadImageWhenProvided() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-IMG")).thenReturn(false);
    when(imageStorageService.uploadProductImage(any()))
        .thenReturn("https://s3.shoploc.fr/prod.jpg");
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    MockMultipartFile file =
        new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[] {1, 2, 3});
    ProductCreateRequest request =
        new ProductCreateRequest("REF-IMG", "Gâteau", "Desc", new BigDecimal("12.00"), null, 2);

    ProductResponse response = productService.createProduct(1L, request, file, 100L, "MERCHANT");

    assertThat(response.getImageUrl()).isEqualTo("https://s3.shoploc.fr/prod.jpg");
  }

  @Test
  @DisplayName("updateProduct keeping own reference should not raise conflict")
  void shouldAllowUpdateProductKeepingOwnReference() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(productRepository.existsByStoreIdAndReferenceAndIdNot(1L, "REF-001", 10L))
        .thenReturn(false);
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    ProductUpdateRequest request =
        new ProductUpdateRequest(
            "REF-001",
            "Croissant Pur Beurre Modifié",
            "Nouvelle recette",
            new BigDecimal("1.70"),
            null,
            25);

    ProductResponse response =
        productService.updateProduct(1L, 10L, request, null, 100L, "MERCHANT");

    assertThat(response.getName()).isEqualTo("Croissant Pur Beurre Modifié");
    assertThat(response.getPrice()).isEqualByComparingTo("1.70");
    assertThat(response.getStockQuantity()).isEqualTo(25);
  }

  @Test
  @DisplayName(
      "updateProduct with reference already used by another product should throw ProductReferenceAlreadyExistsException")
  void shouldThrowWhenUpdateReferenceConflictsWithOtherProduct() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(productRepository.existsByStoreIdAndReferenceAndIdNot(1L, "REF-AUTRE", 10L))
        .thenReturn(true);

    ProductUpdateRequest request =
        new ProductUpdateRequest(
            "REF-AUTRE", "Croissant", "Desc", new BigDecimal("1.50"), null, 20);

    assertThatThrownBy(() -> productService.updateProduct(1L, 10L, request, null, 100L, "MERCHANT"))
        .isInstanceOf(ProductReferenceAlreadyExistsException.class)
        .hasMessageContaining("existe déjà dans le commerce");

    verify(productRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateProduct on non-existent product should throw ProductNotFoundException")
  void shouldThrowWhenUpdatingNonExistentProduct() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(999L, 1L)).thenReturn(Optional.empty());

    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-NEW", "Nom", "Desc", new BigDecimal("2.00"), null, 10);

    assertThatThrownBy(
            () -> productService.updateProduct(1L, 999L, request, null, 100L, "MERCHANT"))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  @DisplayName("updateProduct by non-managing merchant should throw AccessDeniedException")
  void shouldDenyUpdateProductForNonOwnerMerchant() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));

    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-001", "Nom", "Desc", new BigDecimal("2.00"), null, 10);

    assertThatThrownBy(() -> productService.updateProduct(1L, 10L, request, null, 999L, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("vous n'êtes pas le commerçant gestionnaire");
  }

  @Test
  @DisplayName("deleteProduct on non-existent product should throw ProductNotFoundException")
  void shouldThrowWhenDeletingNonExistentProduct() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(999L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.deleteProduct(1L, 999L, 100L, "MERCHANT"))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  @DisplayName("deleteProduct by non-owner merchant should throw AccessDeniedException")
  void shouldDenyDeleteProductForNonOwner() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));

    assertThatThrownBy(() -> productService.deleteProduct(1L, 10L, 999L, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class);

    verify(productRepository, never()).delete(any());
  }

  @Test
  @DisplayName("deleteProduct by owner merchant should delete entity")
  void shouldDeleteProductSuccessfully() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));

    productService.deleteProduct(1L, 10L, 100L, "MERCHANT");

    verify(productRepository).delete(sampleProduct);
  }

  @Test
  @DisplayName("updateStock with SERVICE role should be allowed without user constraint")
  void shouldAllowServiceRoleToUpdateStock() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-3);

    ProductResponse response = productService.updateStock(1L, 10L, request, null, "SERVICE");

    assertThat(response.getStockQuantity()).isEqualTo(17);
    verify(productRepository).save(sampleProduct);
  }

  @Test
  @DisplayName("updateStock with absolute value newQuantity should overwrite available stock")
  void shouldSetAbsoluteStockQuantity() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    StockUpdateRequest request = StockUpdateRequest.ofAbsolute(50);

    ProductResponse response = productService.updateStock(1L, 10L, request, 100L, "MERCHANT");

    assertThat(response.getStockQuantity()).isEqualTo(50);
  }

  @Test
  @DisplayName("updateStock without any field should throw IllegalArgumentException")
  void shouldThrowWhenNoStockUpdateFieldProvided() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(sampleProduct));

    StockUpdateRequest request = new StockUpdateRequest(null, null);

    assertThatThrownBy(() -> productService.updateStock(1L, 10L, request, 100L, "MERCHANT"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Au moins un des champs");
  }

  @Test
  @DisplayName("updateStock by non-managing merchant should throw AccessDeniedException")
  void shouldDenyStockUpdateForNonOwnerMerchant() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-1);

    assertThatThrownBy(() -> productService.updateStock(1L, 10L, request, 999L, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("vous n'êtes pas le commerçant gestionnaire");
  }

  @Test
  @DisplayName(
      "updateStock with decrement exceeding available stock should throw InsufficientStockException")
  void shouldThrowWhenStockBecomesNegative() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreIdForUpdate(10L, 1L))
        .thenReturn(Optional.of(sampleProduct));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-25);

    assertThatThrownBy(() -> productService.updateStock(1L, 10L, request, null, "SERVICE"))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Stock insuffisant");
  }

  @Test
  @DisplayName("updateProductImage uploads to S3 and updates entity")
  void shouldUpdateProductImageSuccessfully() {
    sampleProduct.setImageUrl("https://cdn.shoploc.fr/product-images/old.png");
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    MockMultipartFile newImage =
        new MockMultipartFile("image", "new.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(imageStorageService.uploadProductImage(newImage))
        .thenReturn("https://cdn.shoploc.fr/product-images/new.jpg");

    ProductResponse response =
        productService.updateProductImage(1L, 10L, newImage, 100L, "MERCHANT");

    assertThat(response.getImageUrl()).isEqualTo("https://cdn.shoploc.fr/product-images/new.jpg");
    verify(imageStorageService).deleteImage("https://cdn.shoploc.fr/product-images/old.png");
    verify(productRepository).save(sampleProduct);
  }

  @Test
  @DisplayName("updateProductImageUrl updates URL directly")
  void shouldUpdateProductImageUrlDirectly() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    ProductResponse response =
        productService.updateProductImageUrl(
            1L, 10L, "https://cdn.shoploc.fr/product-images/custom.png", 100L, "MERCHANT");

    assertThat(response.getImageUrl())
        .isEqualTo("https://cdn.shoploc.fr/product-images/custom.png");
    verify(productRepository).save(sampleProduct);
  }

  @Test
  @DisplayName("getProductImage returns binary stream of S3 image")
  void shouldGetProductImageResource() {
    sampleProduct.setImageUrl("https://cdn.shoploc.fr/product-images/pic.png");
    when(storeRepository.existsById(1L)).thenReturn(true);
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(imageStorageService.downloadImage("https://cdn.shoploc.fr/product-images/pic.png"))
        .thenReturn(Optional.of(new ImageResource(new byte[] {4, 5, 6}, "image/png")));

    ImageResource resource = productService.getProductImage(1L, 10L);

    assertThat(resource.getBytes()).isEqualTo(new byte[] {4, 5, 6});
    assertThat(resource.getContentType()).isEqualTo("image/png");
  }

  @Test
  @DisplayName("getProductImage without stored imageUrl throws ProductImageNotFoundException")
  void shouldThrowWhenProductImageIsNull() {
    sampleProduct.setImageUrl(null);
    when(storeRepository.existsById(1L)).thenReturn(true);
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));

    assertThatThrownBy(() -> productService.getProductImage(1L, 10L))
        .isInstanceOf(ProductImageNotFoundException.class);
  }
}
