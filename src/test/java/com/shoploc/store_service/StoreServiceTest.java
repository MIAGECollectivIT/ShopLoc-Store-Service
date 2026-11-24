package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StorePatchRequest;
import com.shoploc.store_service.dto.StoreReactivateRequest;
import com.shoploc.store_service.entity.Store;
import com.shoploc.store_service.entity.StoreStatus;
import com.shoploc.store_service.exception.RevokedStoreException;
import com.shoploc.store_service.exception.StoreAlreadyRevokedException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.exception.StoreNotRevokedException;
import com.shoploc.store_service.exception.UserNotFoundException;
import com.shoploc.store_service.repository.StoreRepository;
import com.shoploc.store_service.service.StoreService;
import com.shoploc.store_service.storage.ImageStorageService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

  @Mock private StoreRepository storeRepository;
  @Mock private UserClient userClient;
  @Mock private ImageStorageService imageStorageService;

  private StoreService storeService;
  private UUID ownerUid;

  @BeforeEach
  void setUp() {
    storeService = new StoreService(storeRepository, userClient, imageStorageService);
    ownerUid = UUID.randomUUID();
  }

  @Test
  @DisplayName("searchStores - Should call findAll(spec) when both query and categories are null")
  void shouldCallFindAllWhenNoFilters() {
    Store store = new Store("Test", "Addr", "url", "Cat", "FR7630006000011234567890189", ownerUid);
    when(storeRepository.findAll(any(Specification.class))).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores(null, null);

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName(
      "searchStores - Should call findAll(spec) when query is blank and categories is empty")
  void shouldCallFindAllWhenBlankAndEmptyFilters() {
    Store store = new Store("Test", "Addr", "url", "Cat", "FR7630006000011234567890189", ownerUid);
    when(storeRepository.findAll(any(Specification.class))).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores("   ", Collections.emptyList());

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName("searchStores - Should call findAll(spec) when query is provided")
  void shouldCallFindAllSpecWhenQueryProvided() {
    Store store =
        new Store("Boulangerie", "Addr", "url", "Cat", "FR7630006000011234567890189", ownerUid);
    when(storeRepository.findAll(any(Specification.class))).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores("boulangerie", null);

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName("searchStores - Should call findAll(spec) when categories are provided")
  void shouldCallFindAllSpecWhenCategoriesProvided() {
    Store store =
        new Store("Test", "Addr", "url", "Boulangerie", "FR7630006000011234567890189", ownerUid);
    when(storeRepository.findAll(any(Specification.class))).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores(null, List.of("Boulangerie"));

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName(
      "searchStores - Should call findAll(spec) when both query and categories are provided")
  void shouldCallFindAllSpecWhenBothProvided() {
    Store store =
        new Store("Paul", "Addr", "url", "Boulangerie", "FR7630006000011234567890189", ownerUid);
    when(storeRepository.findAll(any(Specification.class))).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores("Paul", List.of("Boulangerie"));

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName(
      "revokeStore - Should transition active store to REVOKED, set audit fields and anonymize RIB/URL")
  void shouldRevokeActiveStore() {
    UUID storeUid = UUID.randomUUID();
    Store store =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "FR7630006000011234567890189",
            ownerUid);
    store.setId(1L);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));
    when(storeRepository.save(any(Store.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Store revoked = storeService.revokeStore(storeUid, "admin@ville.fr");

    assertThat(revoked.getStatus()).isEqualTo(StoreStatus.REVOKED);
    assertThat(revoked.isRevoked()).isTrue();
    assertThat(revoked.getRevokedAt()).isNotNull();
    assertThat(revoked.getRevokedBy()).isEqualTo("admin@ville.fr");
    assertThat(revoked.getRib()).isEqualTo("REVOKED");
    assertThat(revoked.getUrl()).isEqualTo("https://cdn.shoploc.fr/img.png");
    verify(storeRepository).save(store);
    verify(userClient).unlinkStoreFromUser(ownerUid, storeUid);
  }

  @Test
  @DisplayName("revokeStore - Should throw StoreNotFoundException when store not found")
  void shouldThrowWhenRevokingNonExistentStore() {
    UUID unknownUid = UUID.randomUUID();
    when(storeRepository.findByUid(unknownUid)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeService.revokeStore(unknownUid, "admin@ville.fr"))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  @DisplayName("revokeStore - Should throw StoreAlreadyRevokedException when store already revoked")
  void shouldThrowWhenRevokingAlreadyRevokedStore() {
    UUID storeUid = UUID.randomUUID();
    Store store =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale",
            null,
            "Alimentation",
            "REVOKED",
            ownerUid);
    store.setId(1L);
    store.setStatus(StoreStatus.REVOKED);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));

    assertThatThrownBy(() -> storeService.revokeStore(storeUid, "admin@ville.fr"))
        .isInstanceOf(StoreAlreadyRevokedException.class);
  }

  @Test
  @DisplayName(
      "reactivateStore - Should transition REVOKED store to ACTIVE, clear audit fields, preserve photo and set new valid RIB")
  void shouldReactivateRevokedStore() {
    UUID storeUid = UUID.randomUUID();
    Store store =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "REVOKED",
            ownerUid);
    store.setId(1L);
    store.setStatus(StoreStatus.REVOKED);
    store.setRevokedAt(java.time.Instant.now());
    store.setRevokedBy("admin@ville.fr");
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));
    when(storeRepository.save(any(Store.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userClient.userExists(ownerUid)).thenReturn(true);

    StoreReactivateRequest request =
        new StoreReactivateRequest("FR76 3000 6000 0112 3456 7890 189");
    Store reactivated = storeService.reactivateStore(storeUid, request);

    assertThat(reactivated.getStatus()).isEqualTo(StoreStatus.ACTIVE);
    assertThat(reactivated.isRevoked()).isFalse();
    assertThat(reactivated.getRevokedAt()).isNull();
    assertThat(reactivated.getRevokedBy()).isNull();
    assertThat(reactivated.getRib()).isEqualTo("FR7630006000011234567890189");
    assertThat(reactivated.getUrl()).isEqualTo("https://cdn.shoploc.fr/img.png");
    verify(storeRepository).save(store);
    verify(userClient).linkStoreToUser(ownerUid, storeUid);
  }

  @Test
  @DisplayName(
      "reactivateStore - Should allow reassigning owner on reactivation and synchronize users-service")
  void shouldReactivateAndReassignOwner() {
    Store store =
        new Store(
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "REVOKED",
            ownerUid);
    store.setId(1L);
    store.setStatus(StoreStatus.REVOKED);
    store.setRevokedAt(java.time.Instant.now());
    store.setRevokedBy("admin@ville.fr");
    UUID storeUid = store.getUid();
    UUID newOwnerUid = UUID.randomUUID();
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));
    when(storeRepository.save(any(Store.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userClient.userExists(newOwnerUid)).thenReturn(true);

    StoreReactivateRequest request =
        new StoreReactivateRequest("FR76 3000 6000 0112 3456 7890 189", newOwnerUid);
    Store reactivated = storeService.reactivateStore(storeUid, request);

    assertThat(reactivated.getStatus()).isEqualTo(StoreStatus.ACTIVE);
    assertThat(reactivated.getOwnerUid()).isEqualTo(newOwnerUid);
    verify(userClient).linkStoreToUser(newOwnerUid, storeUid);
    verify(userClient).unlinkStoreFromUser(ownerUid, storeUid);
  }

  @Test
  @DisplayName(
      "reactivateStore - Should throw StoreNotRevokedException when store is currently ACTIVE")
  void shouldThrowWhenReactivatingActiveStore() {
    UUID storeUid = UUID.randomUUID();
    Store store =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "FR7630006000011234567890189",
            ownerUid);
    store.setId(1L);
    store.setStatus(StoreStatus.ACTIVE);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));

    StoreReactivateRequest request = new StoreReactivateRequest("FR7630006000011234567890189");

    assertThatThrownBy(() -> storeService.reactivateStore(storeUid, request))
        .isInstanceOf(StoreNotRevokedException.class);
  }

  @Test
  @DisplayName("reactivateStore - Should throw IllegalArgumentException when RIB format is invalid")
  void shouldThrowWhenReactivatingWithInvalidRib() {
    UUID storeUid = UUID.randomUUID();
    Store store =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale",
            null,
            "Alimentation",
            "REVOKED",
            ownerUid);
    store.setId(1L);
    store.setStatus(StoreStatus.REVOKED);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));

    StoreReactivateRequest request = new StoreReactivateRequest("INVALID_RIB_123");

    assertThatThrownBy(() -> storeService.reactivateStore(storeUid, request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("revokeStore - Should preserve owner ownerUid and ensure store is never orphan")
  void shouldPreserveOwnerOnRevoke() {
    UUID storeUid = UUID.randomUUID();
    Store store =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "FR7630006000011234567890189",
            ownerUid);
    store.setId(1L);
    store.setStatus(StoreStatus.ACTIVE);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));
    when(storeRepository.save(any(Store.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Store revoked = storeService.revokeStore(storeUid, "admin@ville.fr");

    assertThat(revoked.getStatus()).isEqualTo(StoreStatus.REVOKED);
    assertThat(revoked.getOwnerUid()).isEqualTo(ownerUid);
    verify(userClient).unlinkStoreFromUser(ownerUid, storeUid);
  }

  @Test
  @DisplayName("patchStore - Should allow ADMIN to reassign owner of REVOKED store")
  void shouldAllowAdminToReassignOwnerOfRevokedStore() {
    UUID storeUid = UUID.randomUUID();
    UUID newOwnerUid = UUID.randomUUID();
    UUID adminUid = UUID.randomUUID();
    Store store =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "REVOKED",
            ownerUid);
    store.setId(1L);
    store.setStatus(StoreStatus.REVOKED);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));
    when(userClient.userExists(newOwnerUid)).thenReturn(true);
    when(storeRepository.save(any(Store.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    StorePatchRequest patchReq = new StorePatchRequest();
    patchReq.setOwnerUid(newOwnerUid);

    Store updated = storeService.patchStore(storeUid, patchReq, adminUid, "ADMIN");

    assertThat(updated.getOwnerUid()).isEqualTo(newOwnerUid);
    assertThat(updated.getStatus()).isEqualTo(StoreStatus.REVOKED);

    verify(userClient).linkStoreToUser(newOwnerUid, storeUid);
    verify(userClient).unlinkStoreFromUser(ownerUid, storeUid);
  }

  @Test
  @DisplayName("patchStore - Should reject patching REVOKED store without ownerUid even when ADMIN")
  void shouldRejectPatchingRevokedStoreWithoutUserIdWhenAdmin() {
    UUID storeUid = UUID.randomUUID();
    UUID adminUid = UUID.randomUUID();
    Store store =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "REVOKED",
            ownerUid);
    store.setId(1L);
    store.setStatus(StoreStatus.REVOKED);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));

    StorePatchRequest patchReq = new StorePatchRequest();
    patchReq.setName("Nouveau Nom Interdit");

    assertThatThrownBy(() -> storeService.patchStore(storeUid, patchReq, adminUid, "ADMIN"))
        .isInstanceOf(RevokedStoreException.class);
  }

  @Test
  @DisplayName("patchStore - Should reject reassigning to non-existent user")
  void shouldRejectReassigningToNonExistentUser() {
    UUID storeUid = UUID.randomUUID();
    UUID unknownOwnerUid = UUID.randomUUID();
    UUID adminUid = UUID.randomUUID();
    Store store =
        new Store(
            storeUid,
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "FR7630006000011234567890189",
            ownerUid);
    store.setId(1L);
    store.setStatus(StoreStatus.ACTIVE);
    when(storeRepository.findByUid(storeUid)).thenReturn(Optional.of(store));
    when(userClient.userExists(unknownOwnerUid)).thenReturn(false);

    StorePatchRequest patchReq = new StorePatchRequest();
    patchReq.setOwnerUid(unknownOwnerUid);

    assertThatThrownBy(() -> storeService.patchStore(storeUid, patchReq, adminUid, "ADMIN"))
        .isInstanceOf(UserNotFoundException.class);
  }
}
