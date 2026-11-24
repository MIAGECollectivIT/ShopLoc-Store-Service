package com.shoploc.store_service.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
   * Provides a base {@link RestClient.Builder} with explicit connect/read timeouts, so that a slow
   * or unreachable remote service cannot block Tomcat threads indefinitely (the JDK HttpClient has
   * no read timeout by default). Prototype scope: each client gets its own builder instead of
   * mutating a shared one (base URL, interceptors).
   *
   * @param connectTimeout max time to establish the connection
   * @param readTimeout max time to wait for the response
   * @return a RestClient builder ready for injection
   */
  @Bean
  @Scope("prototype")
  @ConditionalOnMissingBean
  public RestClient.Builder restClientBuilder(
      @Value("${spring.http.client.connect-timeout:2s}") Duration connectTimeout,
      @Value("${spring.http.client.read-timeout:5s}") Duration readTimeout) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().requestFactory(requestFactory);
  }
}
