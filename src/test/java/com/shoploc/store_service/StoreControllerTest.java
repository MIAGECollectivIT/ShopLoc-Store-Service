package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StoreResponse;
import com.shoploc.store_service.exception.UserServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StoreControllerTest {

  @Autowired private StoreController storeController;

  @Autowired private StoreRepository storeRepository;

  @MockitoBean private UserClient userClient;

  @MockitoBean private com.shoploc.store_service.storage.ImageStorageService imageStorageService;

  @Test
  @DisplayName("GET /v1/stores - Should return all stores")
  void shouldReturnAllStores() {
    storeRepository.save(
        new Store(
            "Boutique A",
            "Adresse A",
            "https://img.com/a.jpg",
            "Mode",
            "FR7630006000011234567890189",
            100L));

    List<StoreResponse> stores = storeController.getAllStores(null);

    assertThat(stores).isNotEmpty();
    assertThat(stores).extracting(StoreResponse::getName).contains("Boutique A");
  }

  @Test
  @DisplayName("GET /v1/stores?userId={userId} - Should filter partner stores")
  void shouldFilterStoresByUserId() {
    storeRepository.save(
        new Store(
            "Store User 1",
            "Adresse 1",
            "https://img.com/1.jpg",
            "Epicerie",
            "FR7630006000011234567890189",
            201L));
    storeRepository.save(
        new Store(
            "Store User 2",
            "Adresse 2",
            "https://img.com/2.jpg",
            "Boucherie",
            "FR7630006000011234567890189",
            202L));

    List<StoreResponse> stores = storeController.getAllStores(201L);

    assertThat(stores).hasSize(1);
    assertThat(stores.get(0).getName()).isEqualTo("Store User 1");
    assertThat(stores.get(0).getUserId()).isEqualTo(201L);
  }

  @Test
  @DisplayName("POST /v1/stores - Should successfully create store when partner exists")
  void shouldCreateStoreWhenUserExists() {
    when(userClient.userExists(300L)).thenReturn(true);

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Nouveau Magasin",
            "10 rue du Commerce",
            "https://img.com/store.jpg",
            "Fleuriste",
            "FR7630006000011234567890189",
            300L);

    ResponseEntity<StoreResponse> response =
        storeController.createStore(request, (HttpServletRequest) null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    StoreResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getId()).isNotNull();
    assertThat(body.getName()).isEqualTo("Nouveau Magasin");
    assertThat(body.getCategory()).isEqualTo("Fleuriste");
    assertThat(body.getRib()).isEqualTo("FR7630006000011234567890189");
    assertThat(body.getUserId()).isEqualTo(300L);

    verify(userClient).userExists(300L);
    verify(userClient).linkStoreToUser(eq(300L), eq(body.getId()));
  }

  @Test
  @DisplayName(
      "POST /v1/stores (multipart) - Should upload image and assign generated URL to store")
  void shouldCreateStoreWithUploadedImageWhenMultipartProvided() {
    when(userClient.userExists(301L)).thenReturn(true);
    when(imageStorageService.uploadStoreImage(any()))
        .thenReturn("https://dev.shop-loc.fr/storage/store-images/sample-uuid.jpg");

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Pâtisserie Fine",
            "15 rue Royale, Lille",
            null,
            "Pâtisserie",
            "FR7630006000011234567890189",
            301L);

    org.springframework.mock.web.MockMultipartFile mockFile =
        new org.springframework.mock.web.MockMultipartFile(
            "image", "vitrine.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});

    ResponseEntity<StoreResponse> response = storeController.createStore(request, mockFile, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    StoreResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getUrl())
        .isEqualTo("https://dev.shop-loc.fr/storage/store-images/sample-uuid.jpg");

    verify(imageStorageService).uploadStoreImage(mockFile);
    verify(userClient).linkStoreToUser(eq(301L), eq(body.getId()));
  }

  @Test
  @DisplayName(
      "POST /v1/stores - Should fail with UserNotFoundException when partner does not exist")
  void shouldFailWhenUserDoesNotExist() {
    when(userClient.userExists(999L)).thenReturn(false);

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Magasin Invalide",
            "12 rue Perdue",
            "https://img.com/lost.jpg",
            "Mode",
            "FR7630006000011234567890189",
            999L);

    assertThatThrownBy(() -> storeController.createStore(request, (HttpServletRequest) null))
        .isInstanceOf(com.shoploc.store_service.exception.UserNotFoundException.class)
        .hasMessageContaining("999");

    verify(userClient).userExists(999L);
    verify(userClient, never()).linkStoreToUser(anyLong(), anyLong());
  }

  @Test
  @DisplayName("POST /v1/stores - Should fail when users service is unavailable")
  void shouldFailWhenUserServiceIsUnavailable() {
    when(userClient.userExists(500L))
        .thenThrow(new UserServiceUnavailableException("Service down"));

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Magasin En Panne",
            "12 rue du Réseau",
            "https://img.com/net.jpg",
            "Technologie",
            "FR7630006000011234567890189",
            500L);

    assertThatThrownBy(() -> storeController.createStore(request, (HttpServletRequest) null))
        .isInstanceOf(UserServiceUnavailableException.class);
  }

  @Test
  @DisplayName(
      "GET /v1/stores/{identifier} - Should retrieve store by technical numeric ID as fallback")
  void shouldGetStoreByNumericIdFallback() {
    Store store =
        storeRepository.save(
            new Store(
                "Superette Centrale",
                "8 rue Principale",
                "https://img.com/superette.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                501L));

    ResponseEntity<StoreResponse> response = storeController.getStoreById(store.getId().toString());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getId()).isEqualTo(store.getId());
    assertThat(response.getBody().getUid()).isEqualTo(store.getUid());
    assertThat(response.getBody().getName()).isEqualTo("Superette Centrale");
  }

  @Test
  @DisplayName("GET /v1/stores/{identifier} - Should retrieve store by UUID uid")
  void shouldGetStoreByUid() {
    Store store =
        storeRepository.save(
            new Store(
                "Superette UID",
                "9 rue Principale",
                "https://img.com/superette-uid.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                502L));

    ResponseEntity<StoreResponse> response =
        storeController.getStoreById(store.getUid().toString());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getId()).isEqualTo(store.getId());
    assertThat(response.getBody().getUid()).isEqualTo(store.getUid());
    assertThat(response.getBody().getName()).isEqualTo("Superette UID");
  }

  @Test
  @DisplayName("GET /v1/stores/{identifier} - Should return 404 when store not found by uid")
  void shouldReturnNotFoundWhenStoreNotFoundByUid() {
    java.util.UUID randomUid = java.util.UUID.randomUUID();

    ResponseEntity<StoreResponse> response = storeController.getStoreById(randomUid.toString());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName(
      "GET /v1/stores/{identifier} - Should return 404 when identifier is invalid or blank")
  void shouldReturnNotFoundWhenIdentifierIsInvalidOrBlank() {
    assertThat(storeController.getStoreById("invalid-id-or-uuid").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(storeController.getStoreById("   ").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET /v1/stores/{identifier}/image - Should stream image successfully")
  void shouldGetStoreImageSuccessfully() {
    Store store =
        storeRepository.save(
            new Store(
                "Superette Image",
                "15 rue Principale",
                "https://cdn.shoploc.fr/store-images/superette.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                503L));

    byte[] expectedBytes = new byte[] {0x11, 0x22, 0x33};
    when(imageStorageService.downloadImage("https://cdn.shoploc.fr/store-images/superette.jpg"))
        .thenReturn(
            java.util.Optional.of(
                new com.shoploc.store_service.storage.ImageResource(expectedBytes, "image/jpeg")));

    ResponseEntity<byte[]> response = storeController.getStoreImage(store.getUid().toString());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedBytes);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
    assertThat(response.getHeaders().getCacheControl()).contains("max-age=86400");
  }

  @Test
  @DisplayName(
      "GET /v1/stores/{identifier}/image - Should throw StoreImageNotFoundException when image absent")
  void shouldThrowWhenStoreHasNoImage() {
    Store store =
        storeRepository.save(
            new Store(
                "Superette Sans Image",
                "16 rue Principale",
                null,
                "Alimentation",
                "FR7630006000011234567890189",
                504L));

    assertThatThrownBy(() -> storeController.getStoreImage(store.getUid().toString()))
        .isInstanceOf(com.shoploc.store_service.exception.StoreImageNotFoundException.class);
  }

  @Test
  @DisplayName("GET /v1/stores/search - Should return all stores when no search criteria provided")
  void shouldReturnAllStoresWhenNoSearchCriteriaProvided() {
    storeRepository.save(
        new Store(
            "Boulangerie Test 1",
            "1 rue Test",
            "https://img.com/b1.jpg",
            "Boulangerie",
            "FR7630006000011234567890189",
            601L));
    storeRepository.save(
        new Store(
            "Boucherie Test 2",
            "2 rue Test",
            "https://img.com/b2.jpg",
            "Boucherie",
            "FR7630006000011234567890189",
            602L));

    List<StoreResponse> results = storeController.searchStores(null, null, null, null, null);

    assertThat(results).isNotEmpty();
    assertThat(results)
        .extracting(StoreResponse::getName)
        .contains("Boulangerie Test 1", "Boucherie Test 2");
  }

  @Test
  @DisplayName(
      "GET /v1/stores/search?name={name} - Should filter stores by name case-insensitively")
  void shouldFilterStoresByNameCaseInsensitively() {
    storeRepository.save(
        new Store(
            "Boulangerie Traditionnelle Paul",
            "10 rue Royale",
            "https://img.com/paul.jpg",
            "Boulangerie",
            "FR7630006000011234567890189",
            603L));
    storeRepository.save(
        new Store(
            "Pharmacie Centrale",
            "20 rue Royale",
            "https://img.com/pharm.jpg",
            "Santé",
            "FR7630006000011234567890189",
            604L));

    List<StoreResponse> results =
        storeController.searchStores("traditionnelle", null, null, null, null);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getName()).isEqualTo("Boulangerie Traditionnelle Paul");
  }

  @Test
  @DisplayName("GET /v1/stores/search - Should support 'query' and 'q' parameter aliases")
  void shouldSupportQueryAndQParameterAliases() {
    storeRepository.save(
        new Store(
            "Pâtisserie Gourmande",
            "3 rue Sucrée",
            "https://img.com/patisserie.jpg",
            "Pâtisserie",
            "FR7630006000011234567890189",
            605L));

    List<StoreResponse> resultsQuery =
        storeController.searchStores(null, "gourmande", null, null, null);
    assertThat(resultsQuery).hasSize(1);
    assertThat(resultsQuery.get(0).getName()).isEqualTo("Pâtisserie Gourmande");

    List<StoreResponse> resultsQ =
        storeController.searchStores(null, null, "gourmande", null, null);
    assertThat(resultsQ).hasSize(1);
    assertThat(resultsQ.get(0).getName()).isEqualTo("Pâtisserie Gourmande");
  }

  @Test
  @DisplayName(
      "GET /v1/stores/search?category={category} - Should filter stores by single category")
  void shouldFilterStoresBySingleCategory() {
    storeRepository.save(
        new Store(
            "Boulangerie A",
            "1 rue A",
            "https://img.com/a.jpg",
            "Boulangerie",
            "FR7630006000011234567890189",
            606L));
    storeRepository.save(
        new Store(
            "Boucherie B",
            "2 rue B",
            "https://img.com/b.jpg",
            "Boucherie",
            "FR7630006000011234567890189",
            607L));

    List<StoreResponse> results =
        storeController.searchStores(null, null, null, List.of("boulangerie"), null);

    assertThat(results).extracting(StoreResponse::getName).contains("Boulangerie A");
    assertThat(results).extracting(StoreResponse::getName).doesNotContain("Boucherie B");
  }

  @Test
  @DisplayName(
      "GET /v1/stores/search?categories={categories} - Should filter stores by multiple categories")
  void shouldFilterStoresByMultipleCategories() {
    storeRepository.save(
        new Store(
            "Boulangerie C",
            "1 rue C",
            "https://img.com/c.jpg",
            "Boulangerie",
            "FR7630006000011234567890189",
            608L));
    storeRepository.save(
        new Store(
            "Epicerie Fine D",
            "2 rue D",
            "https://img.com/d.jpg",
            "Epicerie",
            "FR7630006000011234567890189",
            609L));
    storeRepository.save(
        new Store(
            "Fleuriste E",
            "3 rue E",
            "https://img.com/e.jpg",
            "Fleuriste",
            "FR7630006000011234567890189",
            610L));

    List<StoreResponse> results =
        storeController.searchStores(null, null, null, null, List.of("Boulangerie", "Epicerie"));

    assertThat(results)
        .extracting(StoreResponse::getName)
        .contains("Boulangerie C", "Epicerie Fine D")
        .doesNotContain("Fleuriste E");
  }

  @Test
  @DisplayName("GET /v1/stores/search - Should support comma-separated categories")
  void shouldSupportCommaSeparatedCategories() {
    storeRepository.save(
        new Store(
            "Fromagerie F",
            "1 rue F",
            "https://img.com/f.jpg",
            "Fromagerie",
            "FR7630006000011234567890189",
            611L));

    List<StoreResponse> results =
        storeController.searchStores(null, null, null, List.of("Fromagerie, Primeur"), null);

    assertThat(results).extracting(StoreResponse::getName).contains("Fromagerie F");
  }

  @Test
  @DisplayName(
      "GET /v1/stores/search - Should combine name and category filters with AND semantics")
  void shouldCombineNameAndCategoryFilters() {
    storeRepository.save(
        new Store(
            "Le Bon Pain",
            "10 rue Bleue",
            "https://img.com/pain.jpg",
            "Boulangerie",
            "FR7630006000011234567890189",
            612L));
    storeRepository.save(
        new Store(
            "Le Bon Boeuf",
            "12 rue Rouge",
            "https://img.com/boeuf.jpg",
            "Boucherie",
            "FR7630006000011234567890189",
            613L));

    List<StoreResponse> matchResults =
        storeController.searchStores("Le Bon", null, null, List.of("Boulangerie"), null);
    assertThat(matchResults).hasSize(1);
    assertThat(matchResults.get(0).getName()).isEqualTo("Le Bon Pain");

    List<StoreResponse> mismatchResults =
        storeController.searchStores("Le Bon", null, null, List.of("Librairie"), null);
    assertThat(mismatchResults).isEmpty();
  }

  @Test
  @DisplayName("GET /v1/stores/search - Should ignore blank name and empty categories")
  void shouldIgnoreBlankNameAndEmptyCategories() {
    storeRepository.save(
        new Store(
            "Librairie Imaginaire",
            "5 rue des Livres",
            "https://img.com/livres.jpg",
            "Librairie",
            "FR7630006000011234567890189",
            614L));

    List<StoreResponse> results =
        storeController.searchStores("   ", null, null, List.of("   "), List.of());

    assertThat(results).isNotEmpty();
    assertThat(results).extracting(StoreResponse::getName).contains("Librairie Imaginaire");
  }
}
