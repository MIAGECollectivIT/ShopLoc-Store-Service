package com.shoploc.store_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.StoreController;
import com.shoploc.store_service.StoreService;
import com.shoploc.store_service.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StoreSecurityIntegrationTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StoreService storeService;

    @InjectMocks
    private StoreController storeController;

    private final RoleInterceptor roleInterceptor = new RoleInterceptor();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(storeController)
                .addInterceptors(roleInterceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /v1/stores sans header X-User-Role -> doit renvoyer 403 Forbidden")
    void shouldDenyStoreCreationWhenRoleHeaderMissing() throws Exception {
        StoreCreateRequest request = new StoreCreateRequest(
                "Super Boutique",
                "1 rue de la Paix",
                "https://img.com/a.jpg",
                "Mode",
                "FR7630006000011234567890189",
                101L
        );

        mockMvc.perform(post("/v1/stores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Accès refusé : rôle 'ADMIN' requis pour effectuer cette action"));
    }

    @Test
    @DisplayName("POST /v1/stores avec X-User-Role non-ADMIN (ex: CLIENT) -> doit renvoyer 403 Forbidden")
    void shouldDenyStoreCreationWhenRoleIsNotAdmin() throws Exception {
        StoreCreateRequest request = new StoreCreateRequest(
                "Super Boutique",
                "1 rue de la Paix",
                "https://img.com/a.jpg",
                "Mode",
                "FR7630006000011234567890189",
                101L
        );

        mockMvc.perform(post("/v1/stores")
                        .header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("POST /v1/stores avec X-User-Role: ADMIN -> doit autoriser la création (201 Created)")
    void shouldAllowStoreCreationWhenRoleIsAdmin() throws Exception {
        com.shoploc.store_service.Store store = new com.shoploc.store_service.Store(
                "Super Boutique Admin",
                "1 rue de la Paix",
                "https://img.com/a.jpg",
                "Mode",
                "FR7630006000011234567890189",
                101L
        );
        when(storeService.createStore(any(StoreCreateRequest.class))).thenReturn(store);

        StoreCreateRequest request = new StoreCreateRequest(
                "Super Boutique Admin",
                "1 rue de la Paix",
                "https://img.com/a.jpg",
                "Mode",
                "FR7630006000011234567890189",
                101L
        );

        mockMvc.perform(post("/v1/stores")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Super Boutique Admin"))
                .andExpect(jsonPath("$.userId").value(101L));
    }

    @Test
    @DisplayName("GET /v1/stores -> ne nécessite aucun rôle et doit renvoyer 200 OK")
    void shouldAllowPublicStoreConsultation() throws Exception {
        mockMvc.perform(get("/v1/stores"))
                .andExpect(status().isOk());
    }
}
