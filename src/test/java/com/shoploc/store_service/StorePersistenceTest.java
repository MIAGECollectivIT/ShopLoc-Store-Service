package com.shoploc.store_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.entity.Store;
import com.shoploc.store_service.entity.StoreAbsence;
import com.shoploc.store_service.repository.StoreAbsenceRepository;
import com.shoploc.store_service.repository.StoreRepository;
import com.shoploc.store_service.service.StoreService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StorePersistenceTest {

  @Autowired private StoreRepository storeRepository;

  @Autowired private StoreAbsenceRepository storeAbsenceRepository;

  @Autowired private StoreService storeService;

  @MockitoBean private UserClient userClient;

  @Test
  @DisplayName("Should persist store with name, category, RIB, and link to an owner UID")
  void shouldPersistStoreWithCategoryRibAndOwnerUid() {
    UUID ownerUid = UUID.randomUUID();
    Store store =
        new Store(
            "Boulangerie Paul",
            "1 Place Rihour, 59000 Lille",
            "https://example.com/paul.jpg",
            "Boulangerie",
            "FR7630006000011234567890189",
            ownerUid);

    Store savedStore = storeRepository.save(store);

    assertThat(savedStore.getId()).isNotNull();
    assertThat(savedStore.getUid()).isNotNull();
    assertThat(savedStore.getName()).isEqualTo("Boulangerie Paul");
    assertThat(savedStore.getCategory()).isEqualTo("Boulangerie");
    assertThat(savedStore.getRib()).isEqualTo("FR7630006000011234567890189");
    assertThat(savedStore.getOwnerUid()).isEqualTo(ownerUid);
  }

  @Test
  @DisplayName(
      "Should allow the same partner (ownerUid) to own multiple distinct stores (n:1 relation)")
  void shouldAllowOneUserToOwnMultipleStores() {
    UUID ownerUid = UUID.randomUUID();
    Store store1 =
        new Store(
            "Café de Flore Lille",
            "12 Rue de Paris, 59000 Lille",
            "https://example.com/cafe1.jpg",
            "Restauration",
            "FR7630006000011234567890189",
            ownerUid);

    Store store2 =
        new Store(
            "Bistrot du Théâtre",
            "5 Place du Théâtre, 59000 Lille",
            "https://example.com/bistrot.jpg",
            "Restauration",
            "FR7630006000011234567890189",
            ownerUid);

    storeRepository.save(store1);
    storeRepository.save(store2);

    List<Store> partnerStores = storeRepository.findByOwnerUid(ownerUid);

    assertThat(partnerStores).hasSize(2);
    assertThat(partnerStores)
        .extracting(Store::getName)
        .containsExactlyInAnyOrder("Café de Flore Lille", "Bistrot du Théâtre");
  }

  @Test
  @DisplayName("Should create store via StoreService after partner verification")
  void shouldCreateStoreViaServiceWithVerifiedUser() {
    UUID ownerUid = UUID.randomUUID();
    when(userClient.userExists(ownerUid)).thenReturn(true);

    com.shoploc.store_service.dto.StoreCreateRequest request =
        new com.shoploc.store_service.dto.StoreCreateRequest(
            "Librairie du Furet",
            "15 Grand Place, 59000 Lille",
            "https://example.com/furet.jpg",
            "Librairie",
            "FR7630006000011234567890189",
            ownerUid);

    Store created = storeService.createStore(request);

    assertThat(created.getId()).isNotNull();
    assertThat(created.getUid()).isNotNull();
    assertThat(created.getOwnerUid()).isEqualTo(ownerUid);

    List<Store> storesForUser = storeService.getStoresByOwnerUid(ownerUid);
    assertThat(storesForUser).hasSize(1);
    assertThat(storesForUser.get(0).getName()).isEqualTo("Librairie du Furet");
  }

  @Test
  @DisplayName("Should generate UID automatically on persist and allow query by UID")
  void shouldGenerateUidAutomaticallyAndFindStoreByUid() {
    Store store =
        new Store(
            "Pharmacie du Centre",
            "10 Rue de la Paix, 75002 Paris",
            "https://example.com/pharmacie.jpg",
            "Santé",
            "FR7630006000011234567890189",
            UUID.randomUUID());

    Store savedStore = storeRepository.save(store);

    assertThat(savedStore.getUid()).isNotNull();
    assertThat(storeRepository.findByUid(savedStore.getUid())).isPresent();
    assertThat(storeRepository.findByUid(savedStore.getUid()).get().getName())
        .isEqualTo("Pharmacie du Centre");
    assertThat(storeService.getStoreByUid(savedStore.getUid())).isPresent();
    assertThat(storeService.getStoreByUid(savedStore.getUid()).get().getName())
        .isEqualTo("Pharmacie du Centre");
  }

  @Test
  @DisplayName("Should generate UID automatically for StoreAbsence and allow query by UID")
  void shouldGenerateUidForStoreAbsenceAndFindByUid() {
    Store store =
        storeRepository.save(
            new Store(
                "Boulangerie Absence",
                "12 Rue Test",
                null,
                "Boulangerie",
                "FR7630006000011234567890189",
                UUID.randomUUID()));

    StoreAbsence absence =
        new StoreAbsence(
            store,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-10T00:00:00Z"),
            "Congés");
    store.addAbsence(absence);
    Store savedStore = storeRepository.saveAndFlush(store);

    StoreAbsence savedAbsence = savedStore.getAbsences().get(0);
    assertThat(savedAbsence.getUid()).isNotNull();
    assertThat(storeAbsenceRepository.findByUid(savedAbsence.getUid())).isPresent();
    assertThat(
            storeAbsenceRepository.findByStoreIdAndUid(savedStore.getId(), savedAbsence.getUid()))
        .isPresent();
  }
}
