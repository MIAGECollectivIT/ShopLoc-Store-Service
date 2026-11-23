package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StoreAbsenceRequest;
import com.shoploc.store_service.dto.StoreAbsenceResponse;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StorePatchRequest;
import com.shoploc.store_service.dto.StoreReactivateRequest;
import com.shoploc.store_service.dto.StoreResponse;
import com.shoploc.store_service.exception.StoreNotRevokedException;
import com.shoploc.store_service.exception.UserServiceUnavailableException;
import com.shoploc.store_service.security.RoleInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
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
            UUID.randomUUID()));

    List<StoreResponse> stores = storeController.getAllStores(null);

    assertThat(stores).isNotEmpty();
    assertThat(stores).extracting(StoreResponse::getName).contains("Boutique A");
  }

  @Test
  @DisplayName("GET /v1/stores?userId={userId} - Should filter partner stores by ownerUid")
  void shouldFilterStoresByUserId() {
    UUID owner1 = UUID.randomUUID();
    UUID owner2 = UUID.randomUUID();
    storeRepository.save(
        new Store(
            "Store User 1",
            "Adresse 1",
            "https://img.com/1.jpg",
            "Epicerie",
            "FR7630006000011234567890189",
            owner1));
    storeRepository.save(
        new Store(
            "Store User 2",
            "Adresse 2",
            "https://img.com/2.jpg",
            "Boucherie",
            "FR7630006000011234567890189",
            owner2));

    List<StoreResponse> stores = storeController.getAllStores(owner1);

    assertThat(stores).hasSize(1);
    assertThat(stores.get(0).getName()).isEqualTo("Store User 1");
    assertThat(stores.get(0).getOwnerUid()).isEqualTo(owner1);
  }

  @Test
  @DisplayName("POST /v1/stores - Should successfully create store when partner exists")
  void shouldCreateStoreWhenUserExists() {
    UUID ownerUid = UUID.randomUUID();
    when(userClient.userExists(ownerUid)).thenReturn(true);

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Nouveau Magasin",
            "10 rue du Commerce",
            "https://img.com/store.jpg",
            "Fleuriste",
            "FR7630006000011234567890189",
            ownerUid);

    ResponseEntity<StoreResponse> response =
        storeController.createStore(request, (HttpServletRequest) null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    StoreResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getUid()).isNotNull();
    assertThat(body.getName()).isEqualTo("Nouveau Magasin");
    assertThat(body.getCategory()).isEqualTo("Fleuriste");
    assertThat(body.getRib()).isEqualTo("FR7630006000011234567890189");
    assertThat(body.getOwnerUid()).isEqualTo(ownerUid);

    verify(userClient).userExists(ownerUid);
    verify(userClient).linkStoreToUser(eq(ownerUid), eq(body.getUid()));
  }

  @Test
  @DisplayName(
      "POST /v1/stores (multipart) - Should upload image and assign generated URL to store")
  void shouldCreateStoreWithUploadedImageWhenMultipartProvided() {
    UUID ownerUid = UUID.randomUUID();
    when(userClient.userExists(ownerUid)).thenReturn(true);
    when(imageStorageService.uploadStoreImage(any()))
        .thenReturn("https://dev.shop-loc.fr/storage/store-images/sample-uuid.jpg");

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Pâtisserie Fine",
            "15 rue Royale, Lille",
            null,
            "Pâtisserie",
            "FR7630006000011234567890189",
            ownerUid);

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
    verify(userClient).linkStoreToUser(eq(ownerUid), eq(body.getUid()));
  }

  @Test
  @DisplayName(
      "POST /v1/stores - Should fail with UserNotFoundException when partner does not exist")
  void shouldFailWhenUserDoesNotExist() {
    UUID unknownUid = UUID.randomUUID();
    when(userClient.userExists(unknownUid)).thenReturn(false);

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Magasin Invalide",
            "12 rue Perdue",
            "https://img.com/lost.jpg",
            "Mode",
            "FR7630006000011234567890189",
            unknownUid);

    assertThatThrownBy(() -> storeController.createStore(request, (HttpServletRequest) null))
        .isInstanceOf(com.shoploc.store_service.exception.UserNotFoundException.class)
        .hasMessageContaining(unknownUid.toString());

    verify(userClient).userExists(unknownUid);
    verify(userClient, never()).linkStoreToUser(any(), any());
  }

  @Test
  @DisplayName("POST /v1/stores - Should fail when users service is unavailable")
  void shouldFailWhenUserServiceIsUnavailable() {
    UUID ownerUid = UUID.randomUUID();
    when(userClient.userExists(ownerUid))
        .thenThrow(new UserServiceUnavailableException("Service down"));

    StoreCreateRequest request =
        new StoreCreateRequest(
            "Magasin En Panne",
            "12 rue du Réseau",
            "https://img.com/net.jpg",
            "Technologie",
            "FR7630006000011234567890189",
            ownerUid);

    assertThatThrownBy(() -> storeController.createStore(request, (HttpServletRequest) null))
        .isInstanceOf(UserServiceUnavailableException.class);
  }

  @Test
  @DisplayName("GET /v1/stores/{storeUid} - Should retrieve store by UUID uid")
  void shouldGetStoreByUid() {
    Store store =
        storeRepository.save(
            new Store(
                "Superette UID",
                "9 rue Principale",
                "https://img.com/superette-uid.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                UUID.randomUUID()));

    ResponseEntity<StoreResponse> response = storeController.getStoreByUid(store.getUid());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getUid()).isEqualTo(store.getUid());
    assertThat(response.getBody().getOwnerUid()).isEqualTo(store.getOwnerUid());
    assertThat(response.getBody().getName()).isEqualTo("Superette UID");
  }

  @Test
  @DisplayName("GET /v1/stores/{storeUid} - Should return 404 when store not found by uid")
  void shouldReturnNotFoundWhenStoreNotFoundByUid() {
    UUID randomUid = UUID.randomUUID();

    ResponseEntity<StoreResponse> response = storeController.getStoreByUid(randomUid);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("GET /v1/stores/{storeUid}/image - Should stream image successfully")
  void shouldGetStoreImageSuccessfully() {
    Store store =
        storeRepository.save(
            new Store(
                "Superette Image",
                "15 rue Principale",
                "https://cdn.shoploc.fr/store-images/superette.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                UUID.randomUUID()));

    byte[] expectedBytes = new byte[] {0x11, 0x22, 0x33};
    when(imageStorageService.downloadImage("https://cdn.shoploc.fr/store-images/superette.jpg"))
        .thenReturn(
            java.util.Optional.of(
                new com.shoploc.store_service.storage.ImageResource(expectedBytes, "image/jpeg")));

    ResponseEntity<byte[]> response = storeController.getStoreImage(store.getUid());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expectedBytes);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
    assertThat(response.getHeaders().getCacheControl()).contains("max-age=86400");
  }

  @Test
  @DisplayName(
      "GET /v1/stores/{storeUid}/image - Should throw StoreImageNotFoundException when image absent")
  void shouldThrowWhenStoreHasNoImage() {
    Store store =
        storeRepository.save(
            new Store(
                "Superette Sans Image",
                "16 rue Principale",
                null,
                "Alimentation",
                "FR7630006000011234567890189",
                UUID.randomUUID()));

    assertThatThrownBy(() -> storeController.getStoreImage(store.getUid()))
        .isInstanceOf(com.shoploc.store_service.exception.StoreImageNotFoundException.class);
  }

  @Test
  @DisplayName("PATCH /v1/stores/{storeUid} - Should partially update store and absence info")
  void shouldPartiallyUpdateStoreAndAbsence() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Initiale",
                "10 rue Ancienne",
                "https://img.com/initial.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                ownerUid));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    StorePatchRequest patchRequest = new StorePatchRequest();
    patchRequest.setAddress("25 rue Nouvelle");
    patchRequest.setOpeningHours("Lun-Ven: 09h-19h");
    Instant start = Instant.now().plusSeconds(3600);
    Instant end = start.plusSeconds(86400 * 5);
    patchRequest.setAbsenceStartDate(start);
    patchRequest.setAbsenceEndDate(end);
    patchRequest.setAbsenceReason("Conges annuels");

    ResponseEntity<StoreResponse> response =
        storeController.patchStore(store.getUid(), patchRequest, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    StoreResponse updated = response.getBody();
    assertThat(updated).isNotNull();
    assertThat(updated.getName()).isEqualTo("Boutique Initiale");
    assertThat(updated.getAddress()).isEqualTo("25 rue Nouvelle");
    assertThat(updated.getOpeningHours()).isEqualTo("Lun-Ven: 09h-19h");
    assertThat(updated.getAbsenceReason()).isEqualTo("Conges annuels");
    assertThat(updated.getAbsenceDurationDays()).isGreaterThanOrEqualTo(4L);
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeUid} - Should match existing absences by uid during bulk update")
  void shouldUpdateAbsencesMatchingByUidOnBulkPatch() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Bulk Absence",
                "10 rue Bulk",
                "https://img.com/bulk.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                ownerUid));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    ResponseEntity<StoreResponse> addResp =
        storeController.addStoreAbsence(
            store.getUid(),
            new StoreAbsenceRequest(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-05T00:00:00Z"),
                "Initial"),
            servletRequest);
    UUID existingAbsenceUid = addResp.getBody().getAbsences().get(0).getUid();

    StorePatchRequest patchRequest = new StorePatchRequest();
    patchRequest.setAbsences(
        List.of(
            new StoreAbsenceRequest(
                existingAbsenceUid,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-08T00:00:00Z"),
                "Prolongé"),
            new StoreAbsenceRequest(
                Instant.parse("2026-12-24T00:00:00Z"),
                Instant.parse("2026-12-26T00:00:00Z"),
                "Noël")));

    ResponseEntity<StoreResponse> patched =
        storeController.patchStore(store.getUid(), patchRequest, servletRequest);

    assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(patched.getBody().getAbsences()).hasSize(2);
    assertThat(patched.getBody().getAbsences().get(0).getUid()).isEqualTo(existingAbsenceUid);
    assertThat(patched.getBody().getAbsences().get(0).getReason()).isEqualTo("Prolongé");
    assertThat(patched.getBody().getAbsences().get(1).getUid()).isNotNull();
    assertThat(patched.getBody().getAbsences().get(1).getReason()).isEqualTo("Noël");
  }

  @Test
  @DisplayName("PATCH /v1/stores/{storeUid} - Should allow ADMIN to reassign ownerUid")
  void shouldAllowAdminToReassignOwnerUid() {
    UUID initialOwner = UUID.randomUUID();
    UUID newOwner = UUID.randomUUID();
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Reassign",
                "10 rue Admin",
                "https://img.com/admin.jpg",
                "Mode",
                "FR7630006000011234567890189",
                initialOwner));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("ADMIN");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(UUID.randomUUID());

    StorePatchRequest patchRequest = new StorePatchRequest();
    patchRequest.setOwnerUid(newOwner);

    ResponseEntity<StoreResponse> response =
        storeController.patchStore(store.getUid(), patchRequest, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getOwnerUid()).isEqualTo(newOwner);
  }

  @Test
  @DisplayName(
      "PATCH /v1/stores/{storeUid} - Should throw IllegalArgumentException when end date before start date")
  void shouldRejectAbsenceEndDateBeforeStartDate() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Dates",
                "12 rue Test",
                "https://img.com/test.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                ownerUid));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    StorePatchRequest patchRequest = new StorePatchRequest();
    Instant start = Instant.now().plusSeconds(86400);
    Instant end = start.minusSeconds(3600);
    patchRequest.setAbsenceStartDate(start);
    patchRequest.setAbsenceEndDate(end);

    assertThatThrownBy(
            () -> storeController.patchStore(store.getUid(), patchRequest, servletRequest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("La date de fin d'absence ne peut pas être antérieure");
  }

  @Test
  @DisplayName("PATCH /v1/stores/{storeUid} - Should throw AccessDeniedException on IDOR attempt")
  void shouldRejectPatchWhenNotOwner() {
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Autre",
                "14 rue Test",
                "https://img.com/test.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                UUID.randomUUID()));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(UUID.randomUUID());

    StorePatchRequest patchRequest = new StorePatchRequest();
    patchRequest.setName("Piratage");

    assertThatThrownBy(
            () -> storeController.patchStore(store.getUid(), patchRequest, servletRequest))
        .isInstanceOf(com.shoploc.store_service.exception.AccessDeniedException.class);
  }

  @Test
  @DisplayName("PATCH /v1/stores/{storeUid}/image - Should update store image on S3")
  void shouldUpdateStoreImage() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Photo",
                "20 rue Test",
                "https://cdn.shoploc.fr/store-images/old.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                ownerUid));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    org.springframework.web.multipart.MultipartFile imageFile =
        new org.springframework.mock.web.MockMultipartFile(
            "image", "nouvelle.jpg", "image/jpeg", new byte[] {0x10, 0x20});
    when(imageStorageService.uploadStoreImage(imageFile))
        .thenReturn("https://cdn.shoploc.fr/store-images/new.jpg");

    ResponseEntity<StoreResponse> response =
        storeController.updateStoreImage(store.getUid(), imageFile, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getUrl())
        .isEqualTo("https://cdn.shoploc.fr/store-images/new.jpg");
    verify(imageStorageService).deleteImage("https://cdn.shoploc.fr/store-images/old.jpg");
    verify(imageStorageService).uploadStoreImage(imageFile);
  }

  @Test
  void testDeleteStoreAbsence_success() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Absence",
                "25 rue Absence",
                "https://cdn.shoploc.fr/store-images/img.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                ownerUid));
    store.setAbsenceStartDate(Instant.parse("2026-08-01T00:00:00Z"));
    store.setAbsenceEndDate(Instant.parse("2026-08-15T00:00:00Z"));
    store.setAbsenceReason("Conges");
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    ResponseEntity<StoreResponse> response =
        storeController.deleteStoreAbsence(store.getUid(), servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getAbsenceStartDate()).isNull();
    assertThat(response.getBody().getAbsenceEndDate()).isNull();
    assertThat(response.getBody().getAbsenceReason()).isNull();
  }

  @Test
  void testPatchStore_clearAbsenceViaPatch() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Absence Patch",
                "30 rue Absence",
                "https://cdn.shoploc.fr/store-images/img.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                ownerUid));
    store.setAbsenceStartDate(Instant.parse("2026-08-01T00:00:00Z"));
    store.setAbsenceEndDate(Instant.parse("2026-08-15T00:00:00Z"));
    store.setAbsenceReason("Conges");
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    StorePatchRequest patchReq = new StorePatchRequest();
    patchReq.setClearAbsence(true);

    ResponseEntity<StoreResponse> response =
        storeController.patchStore(store.getUid(), patchReq, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getAbsenceStartDate()).isNull();
    assertThat(response.getBody().getAbsenceEndDate()).isNull();
    assertThat(response.getBody().getAbsenceReason()).isNull();
  }

  @Test
  @DisplayName("POST /v1/stores/{storeUid}/absences - Should add an absence to store's list")
  void testAddStoreAbsence_success() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Multi Absence",
                "40 rue Absence",
                "https://cdn.shoploc.fr/img.jpg",
                "Boulangerie",
                "FR7630006000011234567890189",
                ownerUid));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    StoreAbsenceRequest req =
        new StoreAbsenceRequest(
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-15T00:00:00Z"),
            "Fermeture estivale");

    ResponseEntity<StoreResponse> response =
        storeController.addStoreAbsence(store.getUid(), req, servletRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getAbsences()).hasSize(1);
    assertThat(response.getBody().getAbsences().get(0).getUid()).isNotNull();
    assertThat(response.getBody().getAbsences().get(0).getReason()).isEqualTo("Fermeture estivale");
    assertThat(response.getBody().getAbsences().get(0).getDurationDays()).isEqualTo(14L);
  }

  @Test
  @DisplayName(
      "PUT /v1/stores/{storeUid}/absences/{absenceUid} - Should update specific absence in list")
  void testUpdateStoreAbsence_success() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        new Store(
            "Commerce Update Absence",
            "50 rue Absence",
            "https://cdn.shoploc.fr/img.jpg",
            "Fleuriste",
            "FR7630006000011234567890189",
            ownerUid);
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    StoreAbsenceRequest addReq =
        new StoreAbsenceRequest(
            Instant.parse("2026-12-24T00:00:00Z"),
            Instant.parse("2026-12-26T00:00:00Z"),
            "Fetes de fin d'annee");
    ResponseEntity<StoreResponse> addResp =
        storeController.addStoreAbsence(store.getUid(), addReq, servletRequest);
    UUID absenceUid = addResp.getBody().getAbsences().get(0).getUid();

    StoreAbsenceRequest updateReq =
        new StoreAbsenceRequest(
            Instant.parse("2026-12-24T00:00:00Z"),
            Instant.parse("2026-12-28T00:00:00Z"),
            "Vacances de fin d'annee prolongees");

    ResponseEntity<StoreResponse> updateResp =
        storeController.updateStoreAbsence(store.getUid(), absenceUid, updateReq, servletRequest);

    assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updateResp.getBody().getAbsences()).hasSize(1);
    assertThat(updateResp.getBody().getAbsences().get(0).getUid()).isEqualTo(absenceUid);
    assertThat(updateResp.getBody().getAbsences().get(0).getReason())
        .isEqualTo("Vacances de fin d'annee prolongees");
    assertThat(updateResp.getBody().getAbsences().get(0).getDurationDays()).isEqualTo(4L);
  }

  @Test
  @DisplayName(
      "DELETE /v1/stores/{storeUid}/absences/{absenceUid} - Should delete one absence from list")
  void testDeleteStoreAbsenceByUid_success() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        new Store(
            "Commerce Delete One Absence",
            "60 rue Absence",
            "https://cdn.shoploc.fr/img.jpg",
            "Epicerie",
            "FR7630006000011234567890189",
            ownerUid);
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    storeController.addStoreAbsence(
        store.getUid(),
        new StoreAbsenceRequest(
            Instant.parse("2026-05-01T00:00:00Z"),
            Instant.parse("2026-05-03T00:00:00Z"),
            "Pont du 1er mai"),
        servletRequest);

    ResponseEntity<StoreResponse> resp2 =
        storeController.addStoreAbsence(
            store.getUid(),
            new StoreAbsenceRequest(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-15T00:00:00Z"),
                "Fermeture estivale"),
            servletRequest);

    assertThat(resp2.getBody().getAbsences()).hasSize(2);
    UUID firstAbsenceUid = resp2.getBody().getAbsences().get(0).getUid();

    ResponseEntity<StoreResponse> deleteResp =
        storeController.deleteStoreAbsenceByUid(store.getUid(), firstAbsenceUid, servletRequest);

    assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(deleteResp.getBody().getAbsences()).hasSize(1);
    assertThat(deleteResp.getBody().getAbsences().get(0).getReason())
        .isEqualTo("Fermeture estivale");
  }

  @Test
  @DisplayName("GET /v1/stores/{storeUid}/absences - Should retrieve list of absences")
  void testGetStoreAbsences_success() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        new Store(
            "Commerce Get Absences",
            "70 rue Absence",
            "https://cdn.shoploc.fr/img.jpg",
            "Coiffure",
            "FR7630006000011234567890189",
            ownerUid);
    store = storeRepository.save(store);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(ownerUid);

    storeController.addStoreAbsence(
        store.getUid(),
        new StoreAbsenceRequest(
            Instant.parse("2026-07-10T00:00:00Z"),
            Instant.parse("2026-07-20T00:00:00Z"),
            "Inventaire annuel"),
        servletRequest);

    ResponseEntity<List<StoreAbsenceResponse>> resp =
        storeController.getStoreAbsences(store.getUid());

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).hasSize(1);
    assertThat(resp.getBody().get(0).getUid()).isNotNull();
    assertThat(resp.getBody().get(0).getReason()).isEqualTo("Inventaire annuel");
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
            UUID.randomUUID()));
    storeRepository.save(
        new Store(
            "Boucherie Test 2",
            "2 rue Test",
            "https://img.com/b2.jpg",
            "Boucherie",
            "FR7630006000011234567890189",
            UUID.randomUUID()));

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
            UUID.randomUUID()));
    storeRepository.save(
        new Store(
            "Pharmacie Centrale",
            "20 rue Royale",
            "https://img.com/pharm.jpg",
            "Santé",
            "FR7630006000011234567890189",
            UUID.randomUUID()));

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
            UUID.randomUUID()));

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
            UUID.randomUUID()));
    storeRepository.save(
        new Store(
            "Boucherie B",
            "2 rue B",
            "https://img.com/b.jpg",
            "Boucherie",
            "FR7630006000011234567890189",
            UUID.randomUUID()));

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
            UUID.randomUUID()));
    storeRepository.save(
        new Store(
            "Epicerie Fine D",
            "2 rue D",
            "https://img.com/d.jpg",
            "Epicerie",
            "FR7630006000011234567890189",
            UUID.randomUUID()));
    storeRepository.save(
        new Store(
            "Fleuriste E",
            "3 rue E",
            "https://img.com/e.jpg",
            "Fleuriste",
            "FR7630006000011234567890189",
            UUID.randomUUID()));

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
            UUID.randomUUID()));

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
            UUID.randomUUID()));
    storeRepository.save(
        new Store(
            "Le Bon Boeuf",
            "12 rue Rouge",
            "https://img.com/boeuf.jpg",
            "Boucherie",
            "FR7630006000011234567890189",
            UUID.randomUUID()));

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
            UUID.randomUUID()));

    List<StoreResponse> results =
        storeController.searchStores("   ", null, null, List.of("   "), List.of());

    assertThat(results).isNotEmpty();
    assertThat(results).extracting(StoreResponse::getName).contains("Librairie Imaginaire");
  }

  @Test
  @DisplayName("POST /v1/stores/{storeUid}/revoke - Should successfully revoke active store")
  void shouldRevokeStoreWhenAdmin() {
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Sortant",
                "20 rue du Départ",
                "https://img.com/depart.jpg",
                "Mode",
                "FR7630006000011234567890189",
                UUID.randomUUID()));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(RoleInterceptor.ATTR_USER_EMAIL, "admin@ville.fr");

    ResponseEntity<StoreResponse> response = storeController.revokeStore(store.getUid(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(StoreStatus.REVOKED);
    assertThat(response.getBody().getRevokedBy()).isEqualTo("admin@ville.fr");
    assertThat(response.getBody().getRevokedAt()).isNotNull();
    assertThat(response.getBody().getUrl()).isEqualTo("https://img.com/depart.jpg");
    assertThat(response.getBody().getRib()).isEqualTo("REVOKED");
  }

  @Test
  @DisplayName(
      "GET /v1/stores - Should exclude revoked stores by default, but include them with includeRevoked=true")
  void shouldFilterRevokedStoresFromPublicListing() {
    storeRepository.save(
        new Store(
            "Actif Store",
            "1 rue Active",
            "https://img.com/actif.jpg",
            "Mode",
            "FR7630006000011234567890189",
            UUID.randomUUID()));

    Store revokedStore =
        storeRepository.save(
            new Store(
                "Revoked Store",
                "2 rue Morte",
                "https://img.com/mort.jpg",
                "Mode",
                "FR7630006000011234567890189",
                UUID.randomUUID()));
    revokedStore.setStatus(StoreStatus.REVOKED);
    storeRepository.save(revokedStore);

    List<StoreResponse> defaultList = storeController.getAllStores(null, false);
    assertThat(defaultList).extracting(StoreResponse::getName).contains("Actif Store");
    assertThat(defaultList).extracting(StoreResponse::getName).doesNotContain("Revoked Store");

    List<StoreResponse> fullList = storeController.getAllStores(null, true);
    assertThat(fullList)
        .extracting(StoreResponse::getName)
        .contains("Actif Store", "Revoked Store");
  }

  @Test
  @DisplayName(
      "GET /v1/stores/me - Should return stores belonging to authenticated merchant from token")
  void shouldReturnStoresForAuthenticatedUserViaMe() {
    UUID merchantUid = UUID.randomUUID();
    storeRepository.save(
        new Store(
            "Mon Magasin",
            "15 rue du Pain",
            "https://img.com/pain.jpg",
            "Boulangerie",
            "FR7630006000011234567890189",
            merchantUid));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(RoleInterceptor.ATTR_USER_ID, merchantUid);

    List<StoreResponse> myStores = storeController.getMyStores(request);

    assertThat(myStores).hasSize(1);
    assertThat(myStores.get(0).getName()).isEqualTo("Mon Magasin");
    assertThat(myStores.get(0).getOwnerUid()).isEqualTo(merchantUid);
  }

  @Test
  @DisplayName(
      "POST /v1/stores/{storeUid}/reactivate - Should successfully reactivate revoked store with new RIB")
  void shouldReactivateRevokedStoreWhenAdmin() {
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Sortant",
                "20 rue du Départ",
                "https://img.com/depart.jpg",
                "Mode",
                "REVOKED",
                UUID.randomUUID()));
    store.setStatus(StoreStatus.REVOKED);
    store.setRevokedAt(Instant.now());
    store.setRevokedBy("admin@ville.fr");
    store = storeRepository.save(store);

    StoreReactivateRequest request = new StoreReactivateRequest("FR7630006000011234567890189");

    ResponseEntity<StoreResponse> response =
        storeController.reactivateStore(store.getUid(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(StoreStatus.ACTIVE);
    assertThat(response.getBody().getRib()).isEqualTo("FR7630006000011234567890189");
    assertThat(response.getBody().getUrl()).isEqualTo("https://img.com/depart.jpg");
    assertThat(response.getBody().getRevokedAt()).isNull();
    assertThat(response.getBody().getRevokedBy()).isNull();
  }

  @Test
  @DisplayName(
      "POST /v1/stores/{storeUid}/reactivate - Should throw StoreNotRevokedException when store is ACTIVE")
  void shouldFailToReactivateActiveStore() {
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Déjà Actif",
                "10 rue du Centre",
                "https://img.com/actif.jpg",
                "Mode",
                "FR7630006000011234567890189",
                UUID.randomUUID()));

    StoreReactivateRequest request = new StoreReactivateRequest("FR7630006000011234567890189");

    assertThatThrownBy(() -> storeController.reactivateStore(store.getUid(), request))
        .isInstanceOf(StoreNotRevokedException.class);
  }

  @Test
  @DisplayName(
      "SECURITY - GET /v1/stores/{storeUid} - Should anonymize revokedBy on public endpoint to prevent admin email enumeration")
  void shouldNotExposeAdminEmailOnPublicEndpointForRevokedStore() {
    Store store =
        storeRepository.save(
            new Store(
                "Boutique Révoquée Secu",
                "1 rue Discrète",
                "https://img.com/sec.jpg",
                "Services",
                "REVOKED",
                UUID.randomUUID()));
    store.setStatus(StoreStatus.REVOKED);
    store.setRevokedAt(Instant.now());
    store.setRevokedBy("superadmin.secret@shop-loc.fr");
    storeRepository.save(store);

    ResponseEntity<StoreResponse> publicResponse = storeController.getStoreByUid(store.getUid());

    assertThat(publicResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(publicResponse.getBody()).isNotNull();
    assertThat(publicResponse.getBody().getRevokedBy()).isEqualTo("ADMINISTRATEUR_SHOPLOC");
  }

  @Test
  @DisplayName(
      "SECURITY - PATCH /v1/stores/{storeUid} - User 2 should NOT bypass ownership check on store owned by User 1")
  void shouldRejectPatchWhenUser2AttemptsToModifyStoreOwnedByUser1() {
    Store store =
        storeRepository.save(
            new Store(
                "Commerce User 1",
                "10 rue du Marché",
                "https://img.com/marche.jpg",
                "Alimentation",
                "FR7630006000011234567890189",
                UUID.randomUUID()));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(UUID.randomUUID());

    StorePatchRequest patchReq = new StorePatchRequest();
    patchReq.setName("Détournement Commerce");

    assertThatThrownBy(() -> storeController.patchStore(store.getUid(), patchReq, servletRequest))
        .isInstanceOf(com.shoploc.store_service.exception.AccessDeniedException.class);
  }

  @Test
  @DisplayName(
      "SECURITY - PATCH /v1/stores/{storeUid} - Merchant should NOT be able to access another merchant's store (BOLA)")
  void shouldRejectPatchWhenStoreBelongsToDifferentMerchant() {
    Store store =
        storeRepository.save(
            new Store(
                "Commerce Autre Marchand",
                "15 rue Sans Maître",
                "https://img.com/autre.jpg",
                "Commerce",
                "FR7630006000011234567890189",
                UUID.randomUUID()));

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ROLE)).thenReturn("MERCHANT");
    when(servletRequest.getAttribute(RoleInterceptor.ATTR_USER_ID)).thenReturn(UUID.randomUUID());

    StorePatchRequest patchReq = new StorePatchRequest();
    patchReq.setName("Prise De Contrôle");

    assertThatThrownBy(() -> storeController.patchStore(store.getUid(), patchReq, servletRequest))
        .isInstanceOf(com.shoploc.store_service.exception.AccessDeniedException.class);
  }
}
