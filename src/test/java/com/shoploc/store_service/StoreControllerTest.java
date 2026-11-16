package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StoreAbsenceRequest;
import com.shoploc.store_service.dto.StoreAbsenceResponse;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StorePatchRequest;
import com.shoploc.store_service.dto.StoreResponse;
import com.shoploc.store_service.exception.UserServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
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
  @DisplayName("PATCH /v1/stores/{identifier} - Should partially update store and absence info")
  void shouldPartiallyUpdateStoreAndAbsence() {
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Initiale",
                "10 rue Ancienne",
                "https://img.com/initial.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                601L));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(601L);

    com.shoploc.store_service.dto.StorePatchRequest patchRequest =
        new com.shoploc.store_service.dto.StorePatchRequest();
    patchRequest.setAddress("25 rue Nouvelle");
    patchRequest.setOpeningHours("Lun-Ven: 09h-19h");
    java.time.Instant start = java.time.Instant.now().plusSeconds(3600);
    java.time.Instant end = start.plusSeconds(86400 * 5);
    patchRequest.setAbsenceStartDate(start);
    patchRequest.setAbsenceEndDate(end);
    patchRequest.setAbsenceReason("Conges annuels");

    ResponseEntity<StoreResponse> response =
        storeController.patchStore(store.getUid().toString(), patchRequest, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    StoreResponse updated = response.getBody();
    assertThat(updated).isNotNull();
    assertThat(updated.getName()).isEqualTo("Boutique Initiale"); // unchanged
    assertThat(updated.getAddress()).isEqualTo("25 rue Nouvelle"); // updated
    assertThat(updated.getOpeningHours()).isEqualTo("Lun-Ven: 09h-19h");
    assertThat(updated.getAbsenceReason()).isEqualTo("Conges annuels");
    assertThat(updated.getAbsenceDurationDays()).isGreaterThanOrEqualTo(4L);
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{identifier} - Should throw IllegalArgumentException when end date before start date")
  void shouldRejectAbsenceEndDateBeforeStartDate() {
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Dates",
                "12 rue Test",
                "https://img.com/test.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                602L));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(602L);

    com.shoploc.store_service.dto.StorePatchRequest patchRequest =
        new com.shoploc.store_service.dto.StorePatchRequest();
    java.time.Instant start = java.time.Instant.now().plusSeconds(86400);
    java.time.Instant end = start.minusSeconds(3600);
    patchRequest.setAbsenceStartDate(start);
    patchRequest.setAbsenceEndDate(end);

    assertThatThrownBy(
            () ->
                storeController.patchStore(store.getUid().toString(), patchRequest, servletRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("La date de fin d'absence ne peut pas être antérieure");
  }

  @Test
  @DisplayName("PATCH /v1/stores/{identifier} - Should throw AccessDeniedException on IDOR attempt")
  void shouldRejectPatchWhenNotOwner() {
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Autre",
                "14 rue Test",
                "https://img.com/test.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                603L));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(999L); // different user

    com.shoploc.store_service.dto.StorePatchRequest patchRequest =
        new com.shoploc.store_service.dto.StorePatchRequest();
    patchRequest.setName("Piratage");

    assertThatThrownBy(
            () ->
                storeController.patchStore(store.getUid().toString(), patchRequest, servletRequest))
        .isInstanceOf(com.shoploc.store_service.exception.AccessDeniedException.class);
  }

  @Test
  @DisplayName("PATCH /v1/stores/{identifier}/image - Should update store image on S3")
  void shouldUpdateStoreImage() {
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Photo",
                "20 rue Test",
                "https://cdn.shoploc.fr/store-images/old.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                604L));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(604L);

    org.springframework.web.multipart.MultipartFile imageFile =
        new org.springframework.mock.web.MockMultipartFile(
            "image", "nouvelle.jpg", "image/jpeg", new byte[] {0x10, 0x20});
    when(imageStorageService.uploadStoreImage(imageFile))
        .thenReturn("https://cdn.shoploc.fr/store-images/new.jpg");

    ResponseEntity<StoreResponse> response =
        storeController.updateStoreImage(store.getUid().toString(), imageFile, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getUrl())
        .isEqualTo("https://cdn.shoploc.fr/store-images/new.jpg");
    verify(imageStorageService).deleteImage("https://cdn.shoploc.fr/store-images/old.jpg");
    verify(imageStorageService).uploadStoreImage(imageFile);
  }

  @Test
  void testDeleteStoreAbsence_success() {
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Absence",
                "25 rue Absence",
                "https://cdn.shoploc.fr/store-images/img.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                605L));
    store.setAbsenceStartDate(Instant.parse("2026-08-01T00:00:00Z"));
    store.setAbsenceEndDate(Instant.parse("2026-08-15T00:00:00Z"));
    store.setAbsenceReason("Conges");
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(605L);

    ResponseEntity<StoreResponse> response =
        storeController.deleteStoreAbsence(store.getUid().toString(), servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getAbsenceStartDate()).isNull();
    assertThat(response.getBody().getAbsenceEndDate()).isNull();
    assertThat(response.getBody().getAbsenceReason()).isNull();
  }

  @Test
  void testPatchStore_clearAbsenceViaPatch() {
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Absence Patch",
                "30 rue Absence",
                "https://cdn.shoploc.fr/store-images/img.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                606L));
    store.setAbsenceStartDate(Instant.parse("2026-08-01T00:00:00Z"));
    store.setAbsenceEndDate(Instant.parse("2026-08-15T00:00:00Z"));
    store.setAbsenceReason("Conges");
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(606L);

    StorePatchRequest patchReq = new StorePatchRequest();
    patchReq.setClearAbsence(true);

    ResponseEntity<StoreResponse> response =
        storeController.patchStore(store.getUid().toString(), patchReq, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getAbsenceStartDate()).isNull();
    assertThat(response.getBody().getAbsenceEndDate()).isNull();
    assertThat(response.getBody().getAbsenceReason()).isNull();
  }

  @Test
  @DisplayName("POST /v1/stores/{id}/absences - Should add an absence to store's list")
  void testAddStoreAbsence_success() {
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Multi Absence",
                "40 rue Absence",
                "https://cdn.shoploc.fr/img.jpg",
                "Boulangerie",
                "FR7630006000011234567890189",
                701L));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(701L);

    StoreAbsenceRequest req =
        new StoreAbsenceRequest(
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-15T00:00:00Z"),
            "Fermeture estivale");

    ResponseEntity<StoreResponse> response =
        storeController.addStoreAbsence(store.getUid().toString(), req, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getAbsences()).hasSize(1);
    assertThat(response.getBody().getAbsences().get(0).getReason()).isEqualTo("Fermeture estivale");
    assertThat(response.getBody().getAbsences().get(0).getDurationDays()).isEqualTo(14L);
  }

  @Test
  @DisplayName("PUT /v1/stores/{id}/absences/{absenceId} - Should update specific absence in list")
  void testUpdateStoreAbsence_success() {
    Store store =
        new Store(
            "Commerce Update Absence",
            "50 rue Absence",
            "https://cdn.shoploc.fr/img.jpg",
            "Fleuriste",
            "FR7630006000011234567890189",
            702L);
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(702L);

    StoreAbsenceRequest addReq =
        new StoreAbsenceRequest(
            Instant.parse("2026-12-24T00:00:00Z"),
            Instant.parse("2026-12-26T00:00:00Z"),
            "Fetes de fin d'annee");
    ResponseEntity<StoreResponse> addResp =
        storeController.addStoreAbsence(store.getUid().toString(), addReq, servletRequest);
    Long absenceId = addResp.getBody().getAbsences().get(0).getId();

    StoreAbsenceRequest updateReq =
        new StoreAbsenceRequest(
            Instant.parse("2026-12-24T00:00:00Z"),
            Instant.parse("2026-12-28T00:00:00Z"),
            "Vacances de fin d'annee prolongees");

    ResponseEntity<StoreResponse> updateResp =
        storeController.updateStoreAbsence(
            store.getUid().toString(), absenceId, updateReq, servletRequest);

    assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updateResp.getBody().getAbsences()).hasSize(1);
    assertThat(updateResp.getBody().getAbsences().get(0).getReason())
        .isEqualTo("Vacances de fin d'annee prolongees");
    assertThat(updateResp.getBody().getAbsences().get(0).getDurationDays()).isEqualTo(4L);
  }

  @Test
  @DisplayName("DELETE /v1/stores/{id}/absences/{absenceId} - Should delete one absence from list")
  void testDeleteStoreAbsenceById_success() {
    Store store =
        new Store(
            "Commerce Delete One Absence",
            "60 rue Absence",
            "https://cdn.shoploc.fr/img.jpg",
            "Epicerie",
            "FR7630006000011234567890189",
            703L);
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(703L);

    // Add first absence
    storeController.addStoreAbsence(
        store.getUid().toString(),
        new StoreAbsenceRequest(
            Instant.parse("2026-05-01T00:00:00Z"),
            Instant.parse("2026-05-03T00:00:00Z"),
            "Pont du 1er mai"),
        servletRequest);

    // Add second absence
    ResponseEntity<StoreResponse> resp2 =
        storeController.addStoreAbsence(
            store.getUid().toString(),
            new StoreAbsenceRequest(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-15T00:00:00Z"),
                "Fermeture estivale"),
            servletRequest);

    assertThat(resp2.getBody().getAbsences()).hasSize(2);
    Long firstAbsenceId = resp2.getBody().getAbsences().get(0).getId();

    // Delete the first absence
    ResponseEntity<StoreResponse> deleteResp =
        storeController.deleteStoreAbsenceById(
            store.getUid().toString(), firstAbsenceId, servletRequest);

    assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(deleteResp.getBody().getAbsences()).hasSize(1);
    assertThat(deleteResp.getBody().getAbsences().get(0).getReason())
        .isEqualTo("Fermeture estivale");
  }

  @Test
  @DisplayName("GET /v1/stores/{id}/absences - Should retrieve list of absences")
  void testGetStoreAbsences_success() {
    Store store =
        new Store(
            "Commerce Get Absences",
            "70 rue Absence",
            "https://cdn.shoploc.fr/img.jpg",
            "Coiffure",
            "FR7630006000011234567890189",
            704L);
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute("authenticatedUserRole")).thenReturn("MERCHANT");
    when(servletRequest.getAttribute("authenticatedUserDbId")).thenReturn(704L);

    storeController.addStoreAbsence(
        store.getUid().toString(),
        new StoreAbsenceRequest(
            Instant.parse("2026-07-10T00:00:00Z"),
            Instant.parse("2026-07-20T00:00:00Z"),
            "Inventaire annuel"),
        servletRequest);

    ResponseEntity<List<StoreAbsenceResponse>> resp =
        storeController.getStoreAbsences(store.getUid().toString());

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).hasSize(1);
    assertThat(resp.getBody().get(0).getReason()).isEqualTo("Inventaire annuel");
  }
}
