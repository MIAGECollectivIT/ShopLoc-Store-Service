package com.shoploc.store_service.client;

import com.shoploc.store_service.exception.UserServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpUserClientTest {

    private MockRestServiceServer mockServer;
    private HttpUserClient httpUserClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        httpUserClient = new HttpUserClient(builder, "http://mock-users-service");
    }

    @Test
    @DisplayName("userExists - Devrait retourner true si l'utilisateur existe (200 OK)")
    void shouldReturnTrueWhenUserExists() {
        mockServer.expect(requestTo("http://mock-users-service/v1/users/42"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        boolean exists = httpUserClient.userExists(42L);

        assertThat(exists).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("userExists - Devrait retourner false si l'utilisateur n'existe pas (404 Not Found)")
    void shouldReturnFalseWhenUserNotFound() {
        mockServer.expect(requestTo("http://mock-users-service/v1/users/99"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        boolean exists = httpUserClient.userExists(99L);

        assertThat(exists).isFalse();
        mockServer.verify();
    }

    @Test
    @DisplayName("userExists - Devrait lever UserServiceUnavailableException en cas d'erreur 500")
    void shouldThrowExceptionWhenUsersServiceFails() {
        mockServer.expect(requestTo("http://mock-users-service/v1/users/500"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> httpUserClient.userExists(500L))
                .isInstanceOf(UserServiceUnavailableException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("linkStoreToUser - Devrait envoyer une requête POST /v1/users/stores")
    void shouldSendPostRequestToLinkStoreToUser() {
        mockServer.expect(requestTo("http://mock-users-service/v1/users/stores"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.storeId").value(10))
                .andRespond(withSuccess());

        httpUserClient.linkStoreToUser(42L, 10L);

        mockServer.verify();
    }
}
