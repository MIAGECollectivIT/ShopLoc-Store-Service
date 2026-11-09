package com.shoploc.store_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Technical configuration dedicated to Spring dependency injection.
 *
 * <p>Provides a default {@link RestClient.Builder} bean to be injected into HTTP clients (such as
 * HttpUserClient) to avoid NoSuchBeanDefinitionException at startup or in integration tests.
 *
 * <p>Also serves as an extension point for configuring global settings on outgoing requests
 * (timeouts, JWT token propagation, correlation IDs, etc.).
 */
@Configuration
public class RestClientConfig {

  /**
   * Provides a base instance of {@link RestClient.Builder} if none has been registered by Spring
   * Boot auto-configuration.
   *
   * @return a RestClient builder ready for injection
   */
  @Bean
  @ConditionalOnMissingBean
  public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
