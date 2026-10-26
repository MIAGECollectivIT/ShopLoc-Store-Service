package com.shoploc.store_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StorePersistenceTest {

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreService storeService;

    @Test
    @DisplayName("Devrait persister un magasin avec nom, catégorie, RIB et liaison à un utilisateur")
    void shouldPersistStoreWithCategoryRibAndUser() {
        User user = userRepository.save(new User(10L));

        Store store = new Store(
                "Boulangerie Paul",
                "1 Place Rihour, 59000 Lille",
                "https://example.com/paul.jpg",
                "Boulangerie",
                "FR7630006000011234567890189",
                user
        );

        Store savedStore = storeRepository.save(store);

        assertThat(savedStore.getId()).isNotNull();
        assertThat(savedStore.getName()).isEqualTo("Boulangerie Paul");
        assertThat(savedStore.getCategory()).isEqualTo("Boulangerie");
        assertThat(savedStore.getRib()).isEqualTo("FR7630006000011234567890189");
        assertThat(savedStore.getUser()).isNotNull();
        assertThat(savedStore.getUser().getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Devrait autoriser un même partenaire (User) à posséder plusieurs établissements distincts (relation n:1)")
    void shouldAllowOneUserToOwnMultipleStores() {
        User partner = userRepository.save(new User(42L));

        Store store1 = new Store(
                "Café de Flore Lille",
                "12 Rue de Paris, 59000 Lille",
                "https://example.com/cafe1.jpg",
                "Restauration",
                "FR7630006000011234567890189",
                partner
        );

        Store store2 = new Store(
                "Bistrot du Théâtre",
                "5 Place du Théâtre, 59000 Lille",
                "https://example.com/bistrot.jpg",
                "Restauration",
                "FR7630006000011234567890189",
                partner
        );

        storeRepository.save(store1);
        storeRepository.save(store2);

        List<Store> partnerStores = storeRepository.findByUserId(42L);

        assertThat(partnerStores).hasSize(2);
        assertThat(partnerStores).extracting(Store::getName)
                .containsExactlyInAnyOrder("Café de Flore Lille", "Bistrot du Théâtre");
    }

    @Test
    @DisplayName("Devrait créer un store via StoreService et lier automatiquement l'utilisateur")
    void shouldCreateStoreViaServiceWithAutoUser() {
        Store store = new Store(
                "Librairie du Furet",
                "15 Grand Place, 59000 Lille",
                "https://example.com/furet.jpg",
                "Librairie",
                "FR7630006000011234567890189",
                new User(99L)
        );

        Store created = storeService.createStore(store);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getUser().getId()).isEqualTo(99L);
        assertThat(userRepository.existsById(99L)).isTrue();

        List<Store> storesForUser = storeService.getStoresByUserId(99L);
        assertThat(storesForUser).hasSize(1);
        assertThat(storesForUser.get(0).getName()).isEqualTo("Librairie du Furet");
    }
}
