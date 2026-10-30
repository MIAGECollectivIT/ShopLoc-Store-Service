package com.shoploc.store_service;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StoreResponse;
import com.shoploc.store_service.exception.UserServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class StoreControllerTest {

    @Autowired
    private StoreController storeController;

    @Autowired
    private StoreRepository storeRepository;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private com.shoploc.store_service.storage.ImageStorageService imageStorageService;

    @Test
    @DisplayName("GET /v1/stores - Devrait retourner tous les établissements")
    void shouldReturnAllStores() {
        storeRepository.save(new Store("Boutique A", "Adresse A", "https://img.com/a.jpg", "Mode", "FR7630006000011234567890189", 100L));

        List<StoreResponse> stores = storeController.getAllStores(null);

        assertThat(stores).isNotEmpty();
        assertThat(stores).extracting(StoreResponse::getName).contains("Boutique A");
    }

    @Test
    @DisplayName("GET /v1/stores?userId={userId} - Devrait filtrer les établissements du partenaire")
    void shouldFilterStoresByUserId() {
        storeRepository.save(new Store("Store User 1", "Adresse 1", "https://img.com/1.jpg", "Epicerie", "FR7630006000011234567890189", 201L));
        storeRepository.save(new Store("Store User 2", "Adresse 2", "https://img.com/2.jpg", "Boucherie", "FR7630006000011234567890189", 202L));

        List<StoreResponse> stores = storeController.getAllStores(201L);

        assertThat(stores).hasSize(1);
        assertThat(stores.get(0).getName()).isEqualTo("Store User 1");
        assertThat(stores.get(0).getUserId()).isEqualTo(201L);
    }

    @Test
    @DisplayName("POST /v1/stores - Devrait créer un établissement avec succès lorsque le partenaire existe")
    void shouldCreateStoreWhenUserExists() {
        when(userClient.userExists(300L)).thenReturn(true);

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

        verify(userClient).userExists(300L);
        verify(userClient).linkStoreToUser(eq(300L), eq(body.getId()));
    }

    @Test
    @DisplayName("POST /v1/stores (multipart) - Devrait téléverser l'image et assigner l'URL générée au commerce")
    void shouldCreateStoreWithUploadedImageWhenMultipartProvided() {
        when(userClient.userExists(301L)).thenReturn(true);
        when(imageStorageService.uploadStoreImage(any()))
                .thenReturn("https://dev.shop-loc.fr/storage/store-images/sample-uuid.jpg");

        StoreCreateRequest request = new StoreCreateRequest(
                "Pâtisserie Fine",
                "15 rue Royale, Lille",
                null,
                "Pâtisserie",
                "FR7630006000011234567890189",
                301L
        );

        org.springframework.mock.web.MockMultipartFile mockFile = new org.springframework.mock.web.MockMultipartFile(
                "image",
                "vitrine.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        ResponseEntity<StoreResponse> response = storeController.createStore(request, mockFile);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        StoreResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getUrl()).isEqualTo("https://dev.shop-loc.fr/storage/store-images/sample-uuid.jpg");

        verify(imageStorageService).uploadStoreImage(mockFile);
        verify(userClient).linkStoreToUser(eq(301L), eq(body.getId()));
    }

    @Test
    @DisplayName("POST /v1/stores - Devrait échouer avec UserNotFoundException si le partenaire n'existe pas")
    void shouldFailWhenUserDoesNotExist() {
        when(userClient.userExists(999L)).thenReturn(false);

        StoreCreateRequest request = new StoreCreateRequest(
                "Magasin Invalide",
                "12 rue Perdue",
                "https://img.com/lost.jpg",
                "Mode",
                "FR7630006000011234567890189",
                999L
        );

        assertThatThrownBy(() -> storeController.createStore(request))
                .isInstanceOf(com.shoploc.store_service.exception.UserNotFoundException.class)
                .hasMessageContaining("999");

        verify(userClient).userExists(999L);
        verify(userClient, never()).linkStoreToUser(anyLong(), anyLong());
    }

    @Test
    @DisplayName("POST /v1/stores - Devrait échouer si le service utilisateur est indisponible")
    void shouldFailWhenUserServiceIsUnavailable() {
        when(userClient.userExists(500L)).thenThrow(new UserServiceUnavailableException("Service down"));

        StoreCreateRequest request = new StoreCreateRequest(
                "Magasin En Panne",
                "12 rue du Réseau",
                "https://img.com/net.jpg",
                "Technologie",
                "FR7630006000011234567890189",
                500L
        );

        assertThatThrownBy(() -> storeController.createStore(request))
                .isInstanceOf(UserServiceUnavailableException.class);
    }
}
