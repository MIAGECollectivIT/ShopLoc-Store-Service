package com.shoploc.store_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration technique dédiée uniquement à l'injection de dépendances Spring.
 * <p>
 * Fournit un bean {@link RestClient.Builder} par défaut à injecter dans les clients
 * HTTP (comme HttpUserClient) pour éviter les erreurs de type NoSuchBeanDefinitionException
 * au démarrage ou dans les tests d'intégration.
 * <p>
 * Sert également de point d'extension futur pour configurer des paramètres globaux
 * sur les requêtes sortantes (timeouts, propagation de tokens JWT, correlation IDs, etc.).
 */
@Configuration
public class RestClientConfig {

    /**
     * Fournit une instance de base de {@link RestClient.Builder} si aucune n'est déjà
     * enregistrée par l'auto-configuration de Spring Boot.
     *
     * @return un builder de RestClient prêt pour l'injection
     */
    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
