package com.shoploc.store_service.client;

import com.shoploc.store_service.exception.UserServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class HttpUserClient implements UserClient {

    private static final Logger log = LoggerFactory.getLogger(HttpUserClient.class);

    private final RestClient restClient;

    public HttpUserClient(
            RestClient.Builder restClientBuilder,
            @Value("${services.users.url:http://users-service:8080}") String usersServiceBaseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(usersServiceBaseUrl)
                .build();
    }

    @Override
    public boolean userExists(Long userId) {
        if (userId == null) {
            return false;
        }

        try {
            return restClient.get()
                    .uri("/v1/users/{userId}", userId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        // Handled by returning false below
                    })
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            log.error("Client error checking user {}: {}", userId, e.getMessage());
            throw new UserServiceUnavailableException("Erreur lors de la vérification de l'utilisateur: " + e.getMessage(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("Unable to contact users-service: {}", e.getMessage());
            throw new UserServiceUnavailableException("Le service utilisateur est temporairement indisponible", e);
        } catch (Exception e) {
            log.error("Unexpected error contacting users-service for user {}: {}", userId, e.getMessage());
            throw new UserServiceUnavailableException("Erreur lors de la communication avec le service utilisateur", e);
        }
    }

    @Override
    public void linkStoreToUser(Long userId, Long storeId) {
        try {
            restClient.post()
                    .uri("/v1/users/stores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userId", userId, "storeId", storeId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully linked store {} to partner {} in users-service", storeId, userId);
        } catch (Exception e) {
            log.warn("Failed to notify users-service about link between store {} and user {}: {}", storeId, userId, e.getMessage());
        }
    }
}
