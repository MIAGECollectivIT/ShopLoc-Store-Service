package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StoreReactivateRequest;
import com.shoploc.store_service.exception.StoreAlreadyRevokedException;
import com.shoploc.store_service.exception.StoreNotFoundException;
import com.shoploc.store_service.exception.StoreNotRevokedException;
import com.shoploc.store_service.storage.ImageStorageService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

  @BeforeEach
  void setUp() {
    storeService = new StoreService(storeRepository, userClient, imageStorageService);
  }

  @Test
  @DisplayName("searchStores - Should call findAll(spec) when both query and categories are null")
  void shouldCallFindAllWhenNoFilters() {
    Store store = new Store("Test", "Addr", "url", "Cat", "FR7630006000011234567890189", 1L);
    when(storeRepository.findAll(any(Specification.class))).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores(null, null);

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName(
      "searchStores - Should call findAll(spec) when query is blank and categories is empty")
  void shouldCallFindAllWhenBlankAndEmptyFilters() {
    Store store = new Store("Test", "Addr", "url", "Cat", "FR7630006000011234567890189", 1L);
    when(storeRepository.findAll(any(Specification.class))).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores("   ", Collections.emptyList());

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName("searchStores - Should call findAll(spec) when query is provided")
  void shouldCallFindAllSpecWhenQueryProvided() {
    Store store = new Store("Boulangerie", "Addr", "url", "Cat", "FR7630006000011234567890189", 1L);
    when(storeRepository.findAll(any(Specification.class))).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores("boulangerie", null);

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName("searchStores - Should call findAll(spec) when categories are provided")
  void shouldCallFindAllSpecWhenCategoriesProvided() {
    Store store =
        new Store("Test", "Addr", "url", "Boulangerie", "FR7630006000011234567890189", 1L);
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
        new Store("Paul", "Addr", "url", "Boulangerie", "FR7630006000011234567890189", 1L);
    when(storeRepository.findAll(any(Specification.class))).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores("Paul", List.of("Boulangerie"));

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll(any(Specification.class));
  }

  @Test
  @DisplayName(
      "revokeStore - Should transition active store to REVOKED, set audit fields and anonymize RIB/URL")
  void shouldRevokeActiveStore() {
    Store store =
        new Store(
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "FR7630006000011234567890189",
            10L);
    store.setId(1L);
    when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
    when(storeRepository.save(any(Store.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Store revoked = storeService.revokeStore("1", "admin@ville.fr");

    assertThat(revoked.getStatus()).isEqualTo(StoreStatus.REVOKED);
    assertThat(revoked.isRevoked()).isTrue();
    assertThat(revoked.getRevokedAt()).isNotNull();
    assertThat(revoked.getRevokedBy()).isEqualTo("admin@ville.fr");
    assertThat(revoked.getRib()).isEqualTo("REVOKED");
    assertThat(revoked.getUrl()).isEqualTo("https://cdn.shoploc.fr/img.png");
    verify(storeRepository).save(store);
  }

  @Test
  @DisplayName("revokeStore - Should throw StoreNotFoundException when store not found")
  void shouldThrowWhenRevokingNonExistentStore() {
    when(storeRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storeService.revokeStore("999", "admin@ville.fr"))
        .isInstanceOf(StoreNotFoundException.class);
  }

  @Test
  @DisplayName("revokeStore - Should throw StoreAlreadyRevokedException when store already revoked")
  void shouldThrowWhenRevokingAlreadyRevokedStore() {
    Store store =
        new Store("Boulangerie Paul", "10 rue Royale", null, "Alimentation", "REVOKED", 10L);
    store.setId(1L);
    store.setStatus(StoreStatus.REVOKED);
    when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

    assertThatThrownBy(() -> storeService.revokeStore("1", "admin@ville.fr"))
        .isInstanceOf(StoreAlreadyRevokedException.class);
  }

  @Test
  @DisplayName(
      "reactivateStore - Should transition REVOKED store to ACTIVE, clear audit fields, preserve photo and set new valid RIB")
  void shouldReactivateRevokedStore() {
    Store store =
        new Store(
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "REVOKED",
            10L);
    store.setId(1L);
    store.setStatus(StoreStatus.REVOKED);
    store.setRevokedAt(java.time.Instant.now());
    store.setRevokedBy("admin@ville.fr");
    when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
    when(storeRepository.save(any(Store.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    StoreReactivateRequest request =
        new StoreReactivateRequest("FR76 3000 6000 0112 3456 7890 189");
    Store reactivated = storeService.reactivateStore("1", request);

    assertThat(reactivated.getStatus()).isEqualTo(StoreStatus.ACTIVE);
    assertThat(reactivated.isRevoked()).isFalse();
    assertThat(reactivated.getRevokedAt()).isNull();
    assertThat(reactivated.getRevokedBy()).isNull();
    assertThat(reactivated.getRib()).isEqualTo("FR7630006000011234567890189");
    assertThat(reactivated.getUrl()).isEqualTo("https://cdn.shoploc.fr/img.png");
    verify(storeRepository).save(store);
  }

  @Test
  @DisplayName(
      "reactivateStore - Should throw StoreNotRevokedException when store is currently ACTIVE")
  void shouldThrowWhenReactivatingActiveStore() {
    Store store =
        new Store(
            "Boulangerie Paul",
            "10 rue Royale",
            "https://cdn.shoploc.fr/img.png",
            "Alimentation",
            "FR7630006000011234567890189",
            10L);
    store.setId(1L);
    store.setStatus(StoreStatus.ACTIVE);
    when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

    StoreReactivateRequest request = new StoreReactivateRequest("FR7630006000011234567890189");

    assertThatThrownBy(() -> storeService.reactivateStore("1", request))
        .isInstanceOf(StoreNotRevokedException.class);
  }

  @Test
  @DisplayName("reactivateStore - Should throw IllegalArgumentException when RIB format is invalid")
  void shouldThrowWhenReactivatingWithInvalidRib() {
    Store store =
        new Store("Boulangerie Paul", "10 rue Royale", null, "Alimentation", "REVOKED", 10L);
    store.setId(1L);
    store.setStatus(StoreStatus.REVOKED);
    when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

    StoreReactivateRequest request = new StoreReactivateRequest("INVALID_RIB_123");

    assertThatThrownBy(() -> storeService.reactivateStore("1", request))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
