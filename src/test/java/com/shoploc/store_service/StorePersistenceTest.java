package com.shoploc.store_service;

import com.shoploc.store_service.client.UserClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class StorePersistenceTest {

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreService storeService;

    @MockitoBean
    private UserClient userClient;

    @Test
    @DisplayName("Devrait persister un magasin avec nom, catégorie, RIB et liaison à un identifiant utilisateur")
    void shouldPersistStoreWithCategoryRibAndUserId() {
        Store store = new Store(
                "Boulangerie Paul",
                "1 Place Rihour, 59000 Lille",
                "https://example.com/paul.jpg",
                "Boulangerie",
                "FR7630006000011234567890189",
                10L
        );

        Store savedStore = storeRepository.save(store);

        assertThat(savedStore.getId()).isNotNull();
        assertThat(savedStore.getName()).isEqualTo("Boulangerie Paul");
        assertThat(savedStore.getCategory()).isEqualTo("Boulangerie");
        assertThat(savedStore.getRib()).isEqualTo("FR7630006000011234567890189");
        assertThat(savedStore.getUserId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Devrait autoriser un même partenaire (userId) à posséder plusieurs établissements distincts (relation n:1)")
    void shouldAllowOneUserToOwnMultipleStores() {
        Store store1 = new Store(
                "Café de Flore Lille",
                "12 Rue de Paris, 59000 Lille",
                "https://example.com/cafe1.jpg",
                "Restauration",
                "FR7630006000011234567890189",
                42L
        );

        Store store2 = new Store(
                "Bistrot du Théâtre",
                "5 Place du Théâtre, 59000 Lille",
                "https://example.com/bistrot.jpg",
                "Restauration",
                "FR7630006000011234567890189",
                42L
        );

        storeRepository.save(store1);
        storeRepository.save(store2);

        List<Store> partnerStores = storeRepository.findByUserId(42L);

        assertThat(partnerStores).hasSize(2);
        assertThat(partnerStores).extracting(Store::getName)
                .containsExactlyInAnyOrder("Café de Flore Lille", "Bistrot du Théâtre");
    }

    @Test
    @DisplayName("Devrait créer un store via StoreService après vérification du partenaire")
    void shouldCreateStoreViaServiceWithVerifiedUser() {
        when(userClient.userExists(99L)).thenReturn(true);

        Store store = new Store(
                "Librairie du Furet",
                "15 Grand Place, 59000 Lille",
                "https://example.com/furet.jpg",
                "Librairie",
                "FR7630006000011234567890189",
                99L
        );

        Store created = storeService.createStore(store);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getUserId()).isEqualTo(99L);

        List<Store> storesForUser = storeService.getStoresByUserId(99L);
        assertThat(storesForUser).hasSize(1);
        assertThat(storesForUser.get(0).getName()).isEqualTo("Librairie du Furet");
    }
}
