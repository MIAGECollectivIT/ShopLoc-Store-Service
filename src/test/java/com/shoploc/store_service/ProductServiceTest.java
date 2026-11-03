package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    // Propriétaire est 100L, mais le commerçant connecté est 200L
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
  @DisplayName("updateStock avec rôle SERVICE doit être autorisé sans contrainte d'utilisateur")
  void shouldAllowServiceRoleToUpdateStock() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    // Décrémenter 3 unités (ex: commande Click & Collect par Sales Service)
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-3);

    ProductResponse response =
        productService.updateStock(1L, 10L, request, null, "SERVICE");

    assertThat(response.getStockQuantity()).isEqualTo(17);
    verify(productRepository).save(sampleProduct);
  }

  @Test
  @DisplayName("updateStock avec décrément supérieur au stock disponible doit lever InsufficientStockException")
  void shouldThrowWhenStockBecomesNegative() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));

    // Stock actuel = 20, on tente de retirer 25
    StockUpdateRequest request = StockUpdateRequest.ofDelta(-25);

    assertThatThrownBy(() -> productService.updateStock(1L, 10L, request, null, "SERVICE"))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Stock insuffisant");
  }

  @Test
  @DisplayName("deleteProduct par le commerçant propriétaire doit supprimer l'entité")
  void shouldDeleteProductSuccessfully() {
    when(storeRepository.findById(1L)).thenReturn(Optional.of(sampleStore));
    when(productRepository.findByIdAndStoreId(10L, 1L)).thenReturn(Optional.of(sampleProduct));

    productService.deleteProduct(1L, 10L, 100L, "MERCHANT");

    verify(productRepository).delete(sampleProduct);
  }
}
