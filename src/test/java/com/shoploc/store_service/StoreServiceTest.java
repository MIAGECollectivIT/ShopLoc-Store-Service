package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.storage.ImageStorageService;
import java.util.Collections;
import java.util.List;
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
  @DisplayName("searchStores - Should call findAll() when both query and categories are null")
  void shouldCallFindAllWhenNoFilters() {
    Store store = new Store("Test", "Addr", "url", "Cat", "FR7630006000011234567890189", 1L);
    when(storeRepository.findAll()).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores(null, null);

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll();
    verify(storeRepository, never()).findAll(any(Specification.class));
  }

  @Test
  @DisplayName("searchStores - Should call findAll() when query is blank and categories is empty")
  void shouldCallFindAllWhenBlankAndEmptyFilters() {
    Store store = new Store("Test", "Addr", "url", "Cat", "FR7630006000011234567890189", 1L);
    when(storeRepository.findAll()).thenReturn(List.of(store));

    List<Store> result = storeService.searchStores("   ", Collections.emptyList());

    assertThat(result).hasSize(1);
    verify(storeRepository).findAll();
    verify(storeRepository, never()).findAll(any(Specification.class));
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
}
