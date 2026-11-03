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
import com.shoploc.store_service.exception.ProductNotFoundException;
import com.shoploc.store_service.exception.ProductReferenceAlreadyExistsException;
import com.shoploc.store_service.exception.StoreNotFoundException;
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
  @DisplayName("getProductsByStore doit renvoyer la liste des produits du magasin")
  void shouldReturnProductsForStore() {
    when(storeRepository.existsById(1L)).thenReturn(true);
    when(productRepository.findByStoreId(1L)).thenReturn(List.of(sampleProduct));

    List<ProductResponse> responses = productService.getProductsByStore(1L);
    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).getName()).isEqualTo("Croissant au beurre");
    assertThat(responses.get(0).getReference()).isEqualTo("REF-001");
  }

  @Test
  @DisplayName("getProductsByStore sur commerce inexistant doit lancer StoreNotFoundException")
  void shouldThrowWhenStoreNotFoundOnGetProducts() {
    when(storeRepository.existsById(999L)).thenReturn(false);

    assertThatThrownBy(() -> productService.getProductsByStore(999L))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  @DisplayName("getProductById doit retourner le produit s'il existe dans le magasin")
  void shouldReturnProductById() {
    when(storeRepository.existsById(1L)).thenReturn(true);
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));

    ProductResponse response = productService.getProductById(1L, 10L);
    assertThat(response.getId()).isEqualTo(10L);
    assertThat(response.getName()).isEqualTo("Croissant au beurre");
  }

  @Test
  @DisplayName("getProductById sur produit inexistant doit lancer ProductNotFoundException")
  void shouldThrowWhenProductNotFoundById() {
    when(storeRepository.existsById(1L)).thenReturn(true);
    when(productRepository.findByIdAndStoreId(999L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getProductById(1L, 999L))
        .isInstanceOf(ProductNotFoundException.class)
        .hasMessageContaining("999");
  }

  @Test
  @DisplayName("createProduct avec rôle MERCHANT sur son magasin doit réussir")
  void shouldCreateProductForMerchantOwner() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-002")).thenReturn(false);
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
      Product p = inv.getArgument(0);
      p.setId(20L);
      return p;
    });

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-002",
            "Pain au chocolat",
            "Pur beurre",
            new BigDecimal("1.60"),
            null,
            15);

    ProductResponse response =
        productService.createProduct(1L, request, null, 100L, "MERCHANT");

    assertThat(response.getId()).isEqualTo(20L);
    assertThat(response.getName()).isEqualTo("Pain au chocolat");
    assertThat(response.getStockQuantity()).isEqualTo(15);
  }

  @Test
  @DisplayName("createProduct avec rôle MERCHANT sur le magasin d'un autre commerçant doit lever AccessDeniedException")
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
  @DisplayName("createProduct avec rôle ADMIN sur n'importe quel magasin doit réussir")
  void shouldAllowAdminToCreateProductOnAnyStore() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-003")).thenReturn(false);
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
      Product p = inv.getArgument(0);
      p.setId(30L);
      return p;
    });

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-003", "Brioche", "Sucre", new BigDecimal("3.00"), null, 5);

    ProductResponse response =
        productService.createProduct(1L, request, null, 999L, "ADMIN");

    assertThat(response.getId()).isEqualTo(30L);
  }

  @Test
  @DisplayName("createProduct avec référence existante dans le même magasin doit lever ProductReferenceAlreadyExistsException")
  void shouldThrowWhenReferenceAlreadyExistsInStore() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-001")).thenReturn(true);

    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-001", "Doublon", "Desc", new BigDecimal("1.00"), null, 5);

    assertThatThrownBy(() -> productService.createProduct(1L, request, null, 100L, "MERCHANT"))
        .isInstanceOf(ProductReferenceAlreadyExistsException.class)
        .hasMessageContaining("existe déjà dans le commerce");
  }

  @Test
  @DisplayName("createProduct avec image multipart doit appeler imageStorageService")
  void shouldUploadImageWhenProvided() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.existsByStoreIdAndReference(1L, "REF-IMG")).thenReturn(false);
    when(imageStorageService.uploadProductImage(any())).thenReturn("https://s3.shoploc.fr/prod.jpg");
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    MockMultipartFile file =
        new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[] {1, 2, 3});
    ProductCreateRequest request =
        new ProductCreateRequest("REF-IMG", "Gâteau", "Desc", new BigDecimal("12.00"), null, 2);

    ProductResponse response =
        productService.createProduct(1L, request, file, 100L, "MERCHANT");

    assertThat(response.getImageUrl()).isEqualTo("https://s3.shoploc.fr/prod.jpg");
  }

  @Test
  @DisplayName("updateProduct conservant sa propre référence ne doit pas lever de conflit")
  void shouldAllowUpdateProductKeepingOwnReference() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(productRepository.existsByStoreIdAndReferenceAndIdNot(1L, "REF-001", 10L)).thenReturn(false);
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
  @DisplayName("updateProduct avec référence déjà prise par un autre produit doit lever ProductReferenceAlreadyExistsException")
  void shouldThrowWhenUpdateReferenceConflictsWithOtherProduct() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(productRepository.existsByStoreIdAndReferenceAndIdNot(1L, "REF-AUTRE", 10L)).thenReturn(true);

    ProductUpdateRequest request =
        new ProductUpdateRequest(
            "REF-AUTRE", "Croissant", "Desc", new BigDecimal("1.50"), null, 20);

    assertThatThrownBy(() -> productService.updateProduct(1L, 10L, request, null, 100L, "MERCHANT"))
        .isInstanceOf(ProductReferenceAlreadyExistsException.class)
        .hasMessageContaining("existe déjà dans le commerce");

    verify(productRepository, never()).save(any());
  }

  @Test
  @DisplayName("updateProduct sur produit inexistant doit lever ProductNotFoundException")
  void shouldThrowWhenUpdatingNonExistentProduct() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(999L, 1L)).thenReturn(Optional.empty());

    ProductUpdateRequest request =
        new ProductUpdateRequest(
            "REF-NEW", "Nom", "Desc", new BigDecimal("2.00"), null, 10);

    assertThatThrownBy(() -> productService.updateProduct(1L, 999L, request, null, 100L, "MERCHANT"))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  @DisplayName("updateProduct par un commerçant non gérant doit lever AccessDeniedException")
  void shouldDenyUpdateProductForNonOwnerMerchant() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));

    ProductUpdateRequest request =
        new ProductUpdateRequest(
            "REF-001", "Nom", "Desc", new BigDecimal("2.00"), null, 10);

    assertThatThrownBy(() -> productService.updateProduct(1L, 10L, request, null, 999L, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("vous n'êtes pas le commerçant gestionnaire");
  }

  @Test
  @DisplayName("deleteProduct sur produit inexistant doit lever ProductNotFoundException")
  void shouldThrowWhenDeletingNonExistentProduct() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(999L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.deleteProduct(1L, 999L, 100L, "MERCHANT"))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  @DisplayName("deleteProduct par un commerçant non propriétaire doit lever AccessDeniedException")
  void shouldDenyDeleteProductForNonOwner() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));

    assertThatThrownBy(() -> productService.deleteProduct(1L, 10L, 999L, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class);

    verify(productRepository, never()).delete(any());
  }

  @Test
  @DisplayName("deleteProduct par le commerçant propriétaire doit supprimer l'entité")
  void shouldDeleteProductSuccessfully() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));

    productService.deleteProduct(1L, 10L, 100L, "MERCHANT");

    verify(productRepository).delete(sampleProduct);
  }

  @Test
  @DisplayName("updateStock avec rôle SERVICE doit être autorisé sans contrainte d'utilisateur")
  void shouldAllowServiceRoleToUpdateStock() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreIdForUpdate(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-3);

    ProductResponse response =
        productService.updateStock(1L, 10L, request, null, "SERVICE");

    assertThat(response.getStockQuantity()).isEqualTo(17);
    verify(productRepository).save(sampleProduct);
  }

  @Test
  @DisplayName("updateStock avec valeur absolue newQuantity doit écraser le stock disponible")
  void shouldSetAbsoluteStockQuantity() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreIdForUpdate(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    StockUpdateRequest request = StockUpdateRequest.ofAbsolute(50);

    ProductResponse response =
        productService.updateStock(1L, 10L, request, 100L, "MERCHANT");

    assertThat(response.getStockQuantity()).isEqualTo(50);
  }

  @Test
  @DisplayName("updateStock sans aucun champ doit lever IllegalArgumentException")
  void shouldThrowWhenNoStockUpdateFieldProvided() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreIdForUpdate(10L, 1L)).thenReturn(Optional.of(sampleProduct));

    StockUpdateRequest request = new StockUpdateRequest(null, null);

    assertThatThrownBy(() -> productService.updateStock(1L, 10L, request, 100L, "MERCHANT"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Au moins un des champs");
  }

  @Test
  @DisplayName("updateStock par un commerçant non gérant doit lever AccessDeniedException")
  void shouldDenyStockUpdateForNonOwnerMerchant() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-1);

    assertThatThrownBy(() -> productService.updateStock(1L, 10L, request, 999L, "MERCHANT"))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("vous n'êtes pas le commerçant gestionnaire");
  }

  @Test
  @DisplayName("updateStock avec décrément supérieur au stock disponible doit lever InsufficientStockException")
  void shouldThrowWhenStockBecomesNegative() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreIdForUpdate(10L, 1L)).thenReturn(Optional.of(sampleProduct));

    StockUpdateRequest request = StockUpdateRequest.ofDelta(-25);

    assertThatThrownBy(() -> productService.updateStock(1L, 10L, request, null, "SERVICE"))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Stock insuffisant");
  }
}
