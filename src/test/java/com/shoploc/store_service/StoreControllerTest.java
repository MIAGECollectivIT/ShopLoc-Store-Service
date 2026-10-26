package com.shoploc.store_service;

import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StoreResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StoreControllerTest {

    @Autowired
    private StoreController storeController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Test
    @DisplayName("GET /v1/stores - Devrait retourner tous les établissements")
    void shouldReturnAllStores() {
        User user = userRepository.save(new User(100L));
        storeRepository.save(new Store("Boutique A", "Adresse A", "https://img.com/a.jpg", "Mode", "FR7630006000011234567890189", user));

        List<StoreResponse> stores = storeController.getAllStores(null);

        assertThat(stores).isNotEmpty();
        assertThat(stores).extracting(StoreResponse::getName).contains("Boutique A");
    }

    @Test
    @DisplayName("GET /v1/stores?userId={userId} - Devrait filtrer les établissements du partenaire")
    void shouldFilterStoresByUserId() {
        User user1 = userRepository.save(new User(201L));
        User user2 = userRepository.save(new User(202L));

        storeRepository.save(new Store("Store User 1", "Adresse 1", "https://img.com/1.jpg", "Epicerie", "FR7630006000011234567890189", user1));
        storeRepository.save(new Store("Store User 2", "Adresse 2", "https://img.com/2.jpg", "Boucherie", "FR7630006000011234567890189", user2));

        List<StoreResponse> stores = storeController.getAllStores(201L);

        assertThat(stores).hasSize(1);
        assertThat(stores.get(0).getName()).isEqualTo("Store User 1");
        assertThat(stores.get(0).getUserId()).isEqualTo(201L);
    }

    @Test
    @DisplayName("POST /v1/stores - Devrait créer un établissement avec DTO validé")
    void shouldCreateStoreWithValidDto() {
        StoreCreateRequest request = new StoreCreateRequest(
                "Nouveau Magasin",
                "10 rue du Commerce",
                "https://img.com/store.jpg",
                "Fleuriste",
                "FR7630006000011234567890189",
                300L
        );

        ResponseEntity<StoreResponse> response = storeController.createStore(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        StoreResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getName()).isEqualTo("Nouveau Magasin");
        assertThat(body.getCategory()).isEqualTo("Fleuriste");
        assertThat(body.getRib()).isEqualTo("FR7630006000011234567890189");
        assertThat(body.getUserId()).isEqualTo(300L);
    }
}
