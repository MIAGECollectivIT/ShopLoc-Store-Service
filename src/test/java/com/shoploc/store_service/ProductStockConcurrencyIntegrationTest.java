package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoploc.store_service.dto.StockUpdateRequest;
import com.shoploc.store_service.exception.InsufficientStockException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProductStockConcurrencyIntegrationTest {

  @Autowired private ProductService productService;
  @Autowired private ProductRepository productRepository;
  @Autowired private StoreRepository storeRepository;

  private Store store;
  private Product product;

  @BeforeEach
  void setUp() {
    productRepository.deleteAll();
    storeRepository.deleteAll();

    store =
        storeRepository.save(
            new Store(
                "Boulangerie Concurrence",
                "10 rue du Pain",
                "https://s3.shoploc.fr/boulangerie.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                100L));

    product =
        productRepository.save(
            new Product(
                store.getId(),
                "REF-CONC-01",
                "Croissant Beurre",
                "Pur beurre AOP",
                new BigDecimal("1.30"),
                null,
                10));
  }

  @Test
  @DisplayName(
      "Concurrence : 15 requêtes concurrentes de décrément (-1) sur un stock initial de 10 -> exactement 10 réussites, 5 rejets, stock final = 0")
  void shouldHandleConcurrentDecrementsWithoutOverselling() throws Exception {
    int totalRequests = 15;
    int expectedSuccesses = 10;
    int expectedFailures = 5;

    ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
    CountDownLatch readyLatch = new CountDownLatch(totalRequests);
    CountDownLatch startLatch = new CountDownLatch(1);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < totalRequests; i++) {
      futures.add(
          executor.submit(
              () -> {
                readyLatch.countDown();
                try {
                  startLatch.await();
                  productService.updateStock(
                      store.getId(),
                      product.getId(),
                      StockUpdateRequest.ofDelta(-1),
                      null,
                      "SERVICE");
                  successCount.incrementAndGet();
                } catch (InsufficientStockException ex) {
                  failureCount.incrementAndGet();
                } catch (Exception ex) {
                  // Erreur inattendue
                  ex.printStackTrace();
                }
              }));
    }

    // Attendre que tous les threads soient prêts et les libérer simultanément
    readyLatch.await();
    startLatch.countDown();

    for (Future<?> future : futures) {
      future.get();
    }
    executor.shutdown();

    assertThat(successCount.get()).isEqualTo(expectedSuccesses);
    assertThat(failureCount.get()).isEqualTo(expectedFailures);

    Product finalProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(finalProduct.getStockQuantity()).isEqualTo(0);
  }

  @Test
  @DisplayName(
      "Concurrence : 10 requêtes concurrentes d'incrément (+2) sur stock initial 0 -> stock final = 20")
  void shouldHandleConcurrentIncrementsWithoutLostUpdate() throws Exception {
    product.setStockQuantity(0);
    productRepository.save(product);

    int totalThreads = 10;
    int incrementPerThread = 2;

    ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
    CountDownLatch readyLatch = new CountDownLatch(totalThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < totalThreads; i++) {
      futures.add(
          executor.submit(
              () -> {
                readyLatch.countDown();
                try {
                  startLatch.await();
                  productService.updateStock(
                      store.getId(),
                      product.getId(),
                      StockUpdateRequest.ofDelta(incrementPerThread),
                      null,
                      "SERVICE");
                } catch (Exception ex) {
                  ex.printStackTrace();
                }
              }));
    }

    readyLatch.await();
    startLatch.countDown();

    for (Future<?> future : futures) {
      future.get();
    }
    executor.shutdown();

    Product finalProduct = productRepository.findById(product.getId()).orElseThrow();
    assertThat(finalProduct.getStockQuantity()).isEqualTo(totalThreads * incrementPerThread);
  }
}
