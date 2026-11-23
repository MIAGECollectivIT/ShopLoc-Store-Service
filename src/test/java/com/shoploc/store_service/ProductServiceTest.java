package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.ProductCreateRequest;
import com.shoploc.store_service.dto.ProductResponse;
import com.shoploc.store_service.dto.ProductUpdateRequest;
import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.exception.AccessDeniedException;
import com.shoploc.store_service.exception.InsufficientStockException;
import com.shoploc.store_service.exception.ProductImageNotFoundException;
import com.shoploc.store_service.exception.ProductNotFoundException;
import com.shoploc.store_service.exception.ProductReferenceAlreadyExistsException;
import com.shoploc.store_service.exception.RevokedStoreException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.service.StockAlertEmailService;
import com.shoploc.store_service.storage.ImageResource;
import com.shoploc.store_service.storage.ImageStorageService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  @Mock private StockAlertEmailService stockAlertEmailService;
  @Mock private UserClient userClient;

  @InjectMocks private ProductService productService;

  private UUID ownerUid;
  private UUID otherUserUid;
  private UUID storeUid;
  private UUID productUid;
  private Store sampleStore;
  private Product sampleProduct;

  @BeforeEach
  void setUp() {
    ownerUid = UUID.randomUUID();
    otherUserUid = UUID.randomUUID();
    storeUid = UUID.randomUUID();
    productUid = UUID.randomUUID();

    sampleStore =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale, Lille",
            "https://cdn.shoploc.fr/store.jpg",
            "Alimentation",
            "FR7630006000011234567890189",
            ownerUid);
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
    sampleProduct.setUid(productUid);
  }

  @Test
  @DisplayName("getProductsByStore should return product list for store")
  void shouldReturnProductsForStore() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreId(1L)).thenReturn(List.of(sampleProduct));

    List<ProductResponse> responses = productService.getProductsByStore(storeUid);
    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).getUid()).isEqualTo(productUid);
    assertThat(responses.get(0).getStoreUid()).isEqualTo(storeUid);
    assertThat(responses.get(0).getName()).isEqualTo("Croissant au beurre");
    assertThat(responses.get(0).getReference()).isEqualTo("REF-001");
  }

  @Test
  @DisplayName("getProductsByStore on non-existent store should throw StoreNotFoundException")
  void shouldThrowWhenStoreNotFoundOnGetProducts() {
    UUID unknownUid = UUID.randomUUID();
    when(storeRepository.findByUid(unknownUid)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getProductsByStore(unknownUid))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  @DisplayName("getProductByUid should return product when it exists in store")
  void shouldReturnProductByUid() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));

    ProductResponse response = productService.getProductByUid(storeUid, productUid);
    assertThat(response.getUid()).isEqualTo(productUid);
    assertThat(response.getStoreUid()).isEqualTo(storeUid);
    assertThat(response.getName()).isEqualTo("Croissant au beurre");
  }

  @Test
  @DisplayName("getProductByUid on non-existent product should throw ProductNotFoundException")
  void shouldThrowWhenProductNotFoundByUid() {
    UUID unknownProductUid = UUID.randomUUID();
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, unknownProductUid)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getProductByUid(storeUid, unknownProductUid))
        .isInstanceOf(ProductNotFoundException.class)
        .hasMessageContaining(unknownProductUid.toString());
  }

  @Test
  @DisplayName("createProduct with MERCHANT role on owned store should succeed")
  void shouldCreateProductForMerchantOwner() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
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

    ProductResponse response =
        productService.createProduct(storeUid, request, null, ownerUid, "MERCHANT");

    assertThat(response.getUid()).isNotNull();
    assertThat(response.getStoreUid()).isEqualTo(storeUid);
    assertThat(response.getName()).isEqualTo("Pain au chocolat");
    assertThat(response.getStockQuantity()).isEqualTo(15);
  }

  @Test
  @DisplayName(
      "createProduct with MERCHANT role on another merchant's store should throw AccessDeniedException")
  void shouldDenyCreateProductForNonOwnerMerchant() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-002", "Pain au chocolat", "Pur beurre", new BigDecimal("1.60"), null, 15);

    assertThatThrownBy(
            () -> productService.createProduct(storeUid, request, null, otherUserUid, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("vous n'êtes pas le commerçant gestionnaire");
  }

  @Test
  @DisplayName("createProduct with ADMIN role on any store should succeed")
  void shouldAllowAdminToCreateProductOnAnyStore() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
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

    ProductResponse response =
        productService.createProduct(storeUid, request, null, UUID.randomUUID(), "ADMIN");

    assertThat(response.getUid()).isNotNull();
    assertThat(response.getStoreUid()).isEqualTo(storeUid);
  }

  @Test
  @DisplayName(
      "createProduct with existing reference in same store should throw ProductReferenceAlreadyExistsException")
  void shouldThrowWhenReferenceAlreadyExistsInStore() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-001")).thenReturn(true);

    ProductCreateRequest request =
        new ProductCreateRequest("REF-001", "Doublon", "Desc", new BigDecimal("1.00"), null, 5);

    assertThatThrownBy(
            () -> productService.createProduct(storeUid, request, null, ownerUid, "MERCHANT"))
        .isInstanceOf(ProductReferenceAlreadyExistsException.class)
        .hasMessageContaining("existe déjà dans le commerce");
  }

  @Test
  @DisplayName("createProduct with multipart image should call imageStorageService")
  void shouldUploadImageWhenProvided() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-IMG")).thenReturn(false);
    when(imageStorageService.uploadProductImage(any()))
        .thenReturn("https://s3.shoploc.fr/prod.jpg");
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    MockMultipartFile file =
        new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[] {1, 2, 3});
    ProductCreateRequest request =
        new ProductCreateRequest("REF-IMG", "Gâteau", "Desc", new BigDecimal("12.00"), null, 2);

    ProductResponse response =
        productService.createProduct(storeUid, request, file, ownerUid, "MERCHANT");

    assertThat(response.getImageUrl()).isEqualTo("https://s3.shoploc.fr/prod.jpg");
  }

  @Test
  @DisplayName("updateProduct keeping own reference should not raise conflict")
  void shouldAllowUpdateProductKeepingOwnReference() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.existsByStoreIdAndReferenceAndUidNot(1L, "REF-001", productUid))
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
        productService.updateProduct(storeUid, productUid, request, null, ownerUid, "MERCHANT");

    assertThat(response.getName()).isEqualTo("Croissant Pur Beurre Modifié");
    assertThat(response.getPrice()).isEqualByComparingTo("1.70");
    assertThat(response.getStockQuantity()).isEqualTo(25);
  }

  @Test
  @DisplayName(
      "updateProduct with reference already used by another product should throw ProductReferenceAlreadyExistsException")
  void shouldThrowWhenUpdateReferenceConflictsWithOtherProduct() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.existsByStoreIdAndReferenceAndUidNot(1L, "REF-AUTRE", productUid))
        .thenReturn(true);

    ProductUpdateRequest request =
        new ProductUpdateRequest(
            "REF-AUTRE", "Croissant", "Desc", new BigDecimal("1.50"), null, 20);

    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    storeUid, productUid, request, null, ownerUid, "MERCHANT"))
        .isInstanceOf(ProductReferenceAlreadyExistsException.class)
        .hasMessageContaining("existe déjà dans le commerce");

    verify(productRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateProduct on non-existent product should throw ProductNotFoundException")
  void shouldThrowWhenUpdatingNonExistentProduct() {
    UUID unknownProductUid = UUID.randomUUID();
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, unknownProductUid)).thenReturn(Optional.empty());

    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-NEW", "Nom", "Desc", new BigDecimal("2.00"), null, 10);

    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    storeUid, unknownProductUid, request, null, ownerUid, "MERCHANT"))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  @DisplayName("updateProduct by non-managing merchant should throw AccessDeniedException")
  void shouldDenyUpdateProductForNonOwnerMerchant() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));

    ProductUpdateRequest request =
        new ProductUpdateRequest("REF-001", "Nom", "Desc", new BigDecimal("2.00"), null, 10);

    assertThatThrownBy(
            () ->
                productService.updateProduct(
                    storeUid, productUid, request, null, otherUserUid, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("vous n'êtes pas le commerçant gestionnaire");
  }

  @Test
  @DisplayName("deleteProduct on non-existent product should throw ProductNotFoundException")
  void shouldThrowWhenDeletingNonExistentProduct() {
    UUID unknownProductUid = UUID.randomUUID();
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, unknownProductUid)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> productService.deleteProduct(storeUid, unknownProductUid, ownerUid, "MERCHANT"))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  @DisplayName("deleteProduct by non-owner merchant should throw AccessDeniedException")
  void shouldDenyDeleteProductForNonOwner() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));

    assertThatThrownBy(
            () -> productService.deleteProduct(storeUid, productUid, otherUserUid, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class);

    verify(productRepository, never()).delete(any());
  }

  @Test
  @DisplayName("deleteProduct by owner merchant should delete entity")
  void shouldDeleteProductSuccessfully() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));

    productService.deleteProduct(storeUid, productUid, ownerUid, "MERCHANT");

    verify(productRepository).delete(sampleProduct);
  }

  @Test
  @DisplayName("updateStock with SERVICE role should be allowed without user constraint")
  void shouldAllowServiceRoleToUpdateStock() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUidForUpdate(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-3);

    ProductResponse response =
        productService.updateStock(storeUid, productUid, request, null, "SERVICE");

    assertThat(response.getStockQuantity()).isEqualTo(17);
    verify(productRepository).save(sampleProduct);
  }

  @Test
  @DisplayName("updateStock with absolute value newQuantity should overwrite available stock")
  void shouldSetAbsoluteStockQuantity() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUidForUpdate(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    StockUpdateRequest request = StockUpdateRequest.ofAbsolute(50);

    ProductResponse response =
        productService.updateStock(storeUid, productUid, request, ownerUid, "MERCHANT");

    assertThat(response.getStockQuantity()).isEqualTo(50);
  }

  @Test
  @DisplayName("updateStock without any field should throw IllegalArgumentException")
  void shouldThrowWhenNoStockUpdateFieldProvided() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUidForUpdate(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));

    StockUpdateRequest request = new StockUpdateRequest(null, null);

    assertThatThrownBy(
            () -> productService.updateStock(storeUid, productUid, request, ownerUid, "MERCHANT"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Au moins un des champs");
  }

  @Test
  @DisplayName("updateStock reaching critical threshold should trigger stock alert email")
  void shouldTriggerStockAlertWhenReachingThreshold() {
    sampleProduct.setStockQuantity(10);
    sampleProduct.setAlertThreshold(5);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUidForUpdate(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userClient.getUserEmail(ownerUid)).thenReturn(Optional.of("merchant@test.fr"));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-6);
    ProductResponse response =
        productService.updateStock(storeUid, productUid, request, ownerUid, "MERCHANT");

    assertThat(response.getStockQuantity()).isEqualTo(4);
    assertThat(response.getIsAvailable()).isTrue();
    verify(stockAlertEmailService)
        .sendStockAlertAsync(
            eq("merchant@test.fr"),
            eq("Boulangerie Paul"),
            eq("Croissant au beurre"),
            eq("REF-001"),
            eq(4),
            eq(5));
  }

  @Test
  @DisplayName(
      "updateStock reaching zero should trigger rupture de stock alert and set unavailable")
  void shouldTriggerOutOfStockAlertWhenReachingZero() {
    sampleProduct.setStockQuantity(2);
    sampleProduct.setAlertThreshold(5);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUidForUpdate(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userClient.getUserEmail(ownerUid)).thenReturn(Optional.of("merchant@test.fr"));

    StockUpdateRequest request = StockUpdateRequest.ofAbsolute(0);
    ProductResponse response =
        productService.updateStock(storeUid, productUid, request, ownerUid, "MERCHANT");

    assertThat(response.getStockQuantity()).isEqualTo(0);
    assertThat(response.getIsAvailable()).isFalse();
    verify(stockAlertEmailService)
        .sendStockAlertAsync(
            eq("merchant@test.fr"),
            eq("Boulangerie Paul"),
            eq("Croissant au beurre"),
            eq("REF-001"),
            eq(0),
            eq(5));
  }

  @Test
  @DisplayName("updateStock remaining above threshold should not trigger stock alert")
  void shouldNotTriggerStockAlertWhenAboveThreshold() {
    sampleProduct.setStockQuantity(20);
    sampleProduct.setAlertThreshold(5);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUidForUpdate(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-5);
    ProductResponse response =
        productService.updateStock(storeUid, productUid, request, ownerUid, "MERCHANT");

    assertThat(response.getStockQuantity()).isEqualTo(15);
    assertThat(response.getIsAvailable()).isTrue();
    verify(stockAlertEmailService, never())
        .sendStockAlertAsync(any(), any(), any(), any(), anyInt(), anyInt());
  }

  @Test
  @DisplayName("updateStock by non-managing merchant should throw AccessDeniedException")
  void shouldDenyStockUpdateForNonOwnerMerchant() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-1);

    assertThatThrownBy(
            () ->
                productService.updateStock(storeUid, productUid, request, otherUserUid, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("vous n'êtes pas le commerçant gestionnaire");
  }

  @Test
  @DisplayName(
      "updateStock with decrement exceeding available stock should throw InsufficientStockException")
  void shouldThrowWhenStockBecomesNegative() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUidForUpdate(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-25);

    assertThatThrownBy(
            () -> productService.updateStock(storeUid, productUid, request, null, "SERVICE"))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Stock insuffisant");
  }

  @Test
  @DisplayName("updateProductImage uploads to S3 and updates entity")
  void shouldUpdateProductImageSuccessfully() {
    sampleProduct.setImageUrl("https://cdn.shoploc.fr/product-images/old.png");
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    MockMultipartFile newImage =
        new MockMultipartFile("image", "new.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(imageStorageService.uploadProductImage(newImage))
        .thenReturn("https://cdn.shoploc.fr/product-images/new.jpg");

    ProductResponse response =
        productService.updateProductImage(storeUid, productUid, newImage, ownerUid, "MERCHANT");

    assertThat(response.getImageUrl()).isEqualTo("https://cdn.shoploc.fr/product-images/new.jpg");
    verify(imageStorageService).deleteImage("https://cdn.shoploc.fr/product-images/old.png");
    verify(productRepository).save(sampleProduct);
  }

  @Test
  @DisplayName("updateProductImageUrl updates URL directly")
  void shouldUpdateProductImageUrlDirectly() {
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    ProductResponse response =
        productService.updateProductImageUrl(
            storeUid,
            productUid,
            "https://cdn.shoploc.fr/product-images/custom.png",
            ownerUid,
            "MERCHANT");

    assertThat(response.getImageUrl())
        .isEqualTo("https://cdn.shoploc.fr/product-images/custom.png");
    verify(productRepository).save(sampleProduct);
  }

  @Test
  @DisplayName("getProductImage returns binary stream of S3 image")
  void shouldGetProductImageResource() {
    sampleProduct.setImageUrl("https://cdn.shoploc.fr/product-images/pic.png");
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));
    when(imageStorageService.downloadImage("https://cdn.shoploc.fr/product-images/pic.png"))
        .thenReturn(Optional.of(new ImageResource(new byte[] {4, 5, 6}, "image/png")));

    ImageResource resource = productService.getProductImage(storeUid, productUid);

    assertThat(resource.getBytes()).isEqualTo(new byte[] {4, 5, 6});
    assertThat(resource.getContentType()).isEqualTo("image/png");
  }

  @Test
  @DisplayName("getProductImage without stored imageUrl throws ProductImageNotFoundException")
  void shouldThrowWhenProductImageIsNull() {
    sampleProduct.setImageUrl(null);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByStoreIdAndUid(1L, productUid))
        .thenReturn(Optional.of(sampleProduct));

    assertThatThrownBy(() -> productService.getProductImage(storeUid, productUid))
        .isInstanceOf(ProductImageNotFoundException.class);
  }

  @Test
  @DisplayName("getProductsByStore on revoked store should return empty list")
  void shouldReturnEmptyListForRevokedStore() {
    UUID revokedUid = UUID.randomUUID();
    Store revokedStore =
        new Store(revokedUid, "Magasin Révoqué", "1 rue Morte", null, "Mode", "REVOKED", ownerUid);
    revokedStore.setId(2L);
    revokedStore.setStatus(StoreStatus.REVOKED);
    when(storeRepository.findByUid(revokedUid)).thenReturn(Optional.of(revokedStore));

    List<ProductResponse> products = productService.getProductsByStore(revokedUid);
    assertThat(products).isEmpty();
    verify(productRepository, never()).findByStoreId(any());
  }

  @Test
  @DisplayName("createProduct on revoked store should throw RevokedStoreException")
  void shouldThrowRevokedStoreExceptionOnCreateProduct() {
    UUID revokedUid = UUID.randomUUID();
    Store revokedStore =
        new Store(revokedUid, "Magasin Révoqué", "1 rue Morte", null, "Mode", "REVOKED", ownerUid);
    revokedStore.setId(2L);
    revokedStore.setStatus(StoreStatus.REVOKED);
    when(storeRepository.findByUid(revokedUid)).thenReturn(Optional.of(revokedStore));

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-REVOKED", "Produit", "Desc", new BigDecimal("10.00"), null, 5);

    assertThatThrownBy(
            () -> productService.createProduct(revokedUid, request, null, ownerUid, "MERCHANT"))
        .isInstanceOf(RevokedStoreException.class);
  }

  @Test
  @DisplayName("updateStock on revoked store should throw RevokedStoreException")
  void shouldThrowRevokedStoreExceptionOnUpdateStock() {
    UUID revokedUid = UUID.randomUUID();
    Store revokedStore =
        new Store(revokedUid, "Magasin Révoqué", "1 rue Morte", null, "Mode", "REVOKED", ownerUid);
    revokedStore.setId(2L);
    revokedStore.setStatus(StoreStatus.REVOKED);
    when(storeRepository.findByUid(revokedUid)).thenReturn(Optional.of(revokedStore));

    StockUpdateRequest request = new StockUpdateRequest(5, null);

    assertThatThrownBy(
            () -> productService.updateStock(revokedUid, productUid, request, ownerUid, "MERCHANT"))
        .isInstanceOf(RevokedStoreException.class);
  }
}
