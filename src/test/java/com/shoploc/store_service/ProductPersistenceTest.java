package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProductPersistenceTest {

  @Autowired private StoreRepository storeRepository;

  @Autowired private ProductRepository productRepository;

  private Store testStore;
  private Store otherStore;

  @BeforeEach
  void setUp() {
    testStore =
        storeRepository.save(
            new Store(
                "Boulangerie Test",
                "10 rue des Fleurs, Lille",
                "https://cdn.shoploc.fr/store.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                100L));

    otherStore =
        storeRepository.save(
            new Store(
                "Boucherie Test",
                "20 rue Royale, Lille",
                "https://cdn.shoploc.fr/other.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                101L));
  }

  @Test
  @DisplayName("Devrait persister un produit avec tous ses champs et timestamps automatiques")
  void shouldPersistProductWithAllFieldsAndTimestamps() {
    Product product =
        new Product(
            testStore.getId(),
            "REF-PAIN-01",
            "Pain de seigle",
            "Farine biologique T130",
            new BigDecimal("2.40"),
            "https://cdn.shoploc.fr/seigle.jpg",
            15);

    Product saved = productRepository.saveAndFlush(product);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getStoreId()).isEqualTo(testStore.getId());
    assertThat(saved.getReference()).isEqualTo("REF-PAIN-01");
    assertThat(saved.getName()).isEqualTo("Pain de seigle");
    assertThat(saved.getDescription()).isEqualTo("Farine biologique T130");
    assertThat(saved.getPrice()).isEqualByComparingTo("2.40");
    assertThat(saved.getStockQuantity()).isEqualTo(15);
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("Devrait interdire deux produits ayant la même référence dans le même magasin (unicité composite)")
  void shouldRejectDuplicateReferenceInSameStore() {
    Product p1 =
        new Product(
            testStore.getId(),
            "REF-UNIQUE-01",
            "Produit Un",
            "Desc",
            new BigDecimal("1.00"),
            null,
            10);
    productRepository.saveAndFlush(p1);

    Product p2 =
        new Product(
            testStore.getId(),
            "REF-UNIQUE-01",
            "Produit Deux (doublon)",
            "Desc",
            new BigDecimal("2.00"),
            null,
            5);

    assertThatThrownBy(() -> productRepository.saveAndFlush(p2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Devrait autoriser la même référence dans deux magasins différents")
  void shouldAllowSameReferenceInDifferentStores() {
    Product p1 =
        new Product(
            testStore.getId(),
            "REF-COMMUN-01",
            "Baguette",
            "Desc",
            new BigDecimal("1.20"),
            null,
            20);
    Product p2 =
        new Product(
            otherStore.getId(),
            "REF-COMMUN-01",
            "Baguette autre magasin",
            "Desc",
            new BigDecimal("1.30"),
            null,
            15);

    productRepository.saveAndFlush(p1);
    Product savedP2 = productRepository.saveAndFlush(p2);

    assertThat(savedP2.getId()).isNotNull();
    assertThat(productRepository.findByStoreId(testStore.getId())).hasSize(1);
    assertThat(productRepository.findByStoreId(otherStore.getId())).hasSize(1);
  }

  @Test
  @DisplayName("Devrait supprimer un produit et vérifier l'isolation du catalogue magasin")
  void shouldDeleteProductAndVerifyStoreCatalogIsolation() {
    Product p1 =
        new Product(
            testStore.getId(), "REF-1", "Article 1", "Desc", new BigDecimal("1.00"), null, 5);
    Product p2 =
        new Product(
            testStore.getId(), "REF-2", "Article 2", "Desc", new BigDecimal("2.00"), null, 10);
    productRepository.saveAndFlush(p1);
    Product savedP2 = productRepository.saveAndFlush(p2);

    assertThat(productRepository.findByStoreId(testStore.getId())).hasSize(2);

    productRepository.delete(savedP2);
    productRepository.flush();

    List<Product> remaining = productRepository.findByStoreId(testStore.getId());
    assertThat(remaining).hasSize(1);
    assertThat(remaining.get(0).getReference()).isEqualTo("REF-1");
  }

  @Test
  @DisplayName("Devrait mettre à jour updatedAt lors d'une modification du produit")
  void shouldUpdateTimestampOnModification() throws InterruptedException {
    Product product =
        new Product(
            testStore.getId(), "REF-TIME", "Article", "Desc", new BigDecimal("5.00"), null, 10);
    Product saved = productRepository.saveAndFlush(product);
    var initialUpdatedAt = saved.getUpdatedAt();

    Thread.sleep(10);
    saved.setStockQuantity(25);
    Product updated = productRepository.saveAndFlush(saved);

    assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(initialUpdatedAt);
    assertThat(updated.getStockQuantity()).isEqualTo(25);
  }
}
