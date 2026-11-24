package com.shoploc.store_service.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Vérifie que le RestClient.Builder auto-configuré applique bien un read-timeout. */
@SpringBootTest(properties = {"spring.http.client.read-timeout=300ms"})
class HttpClientTimeoutTest {

  private static HttpServer slowServer;

  @Autowired private RestClient.Builder builder;

  @BeforeAll
  static void startSlowServer() throws Exception {
    slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    slowServer.createContext(
        "/slow",
        exchange -> {
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    slowServer.start();
  }

  @AfterAll
  static void stopSlowServer() {
    slowServer.stop(0);
  }

  @Test
  void slowRemoteServiceTriggersReadTimeout() {
    RestClient client =
        builder.baseUrl("http://127.0.0.1:" + slowServer.getAddress().getPort()).build();

    assertThatThrownBy(() -> client.get().uri("/slow").retrieve().toBodilessEntity())
        .isInstanceOf(ResourceAccessException.class);
  }
}
