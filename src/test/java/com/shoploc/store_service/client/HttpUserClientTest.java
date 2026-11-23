package com.shoploc.store_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.shoploc.store_service.exception.UserServiceUnavailableException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
  @DisplayName("userExists - Should return true when user exists (200 OK)")
  void shouldReturnTrueWhenUserExists() {
    UUID userId = UUID.randomUUID();
    mockServer
        .expect(requestTo("http://mock-users-service/v1/users/" + userId))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess());

    boolean exists = httpUserClient.userExists(userId);

    assertThat(exists).isTrue();
    mockServer.verify();
  }

  @Test
  @DisplayName("userExists - Should return false when user does not exist (404 Not Found)")
  void shouldReturnFalseWhenUserNotFound() {
    UUID userId = UUID.randomUUID();
    mockServer
        .expect(requestTo("http://mock-users-service/v1/users/" + userId))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    boolean exists = httpUserClient.userExists(userId);

    assertThat(exists).isFalse();
    mockServer.verify();
  }

  @Test
  @DisplayName("userExists - Should throw UserServiceUnavailableException on 500 server error")
  void shouldThrowExceptionWhenUsersServiceFails() {
    UUID userId = UUID.randomUUID();
    mockServer
        .expect(requestTo("http://mock-users-service/v1/users/" + userId))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withServerError());

    assertThatThrownBy(() -> httpUserClient.userExists(userId))
        .isInstanceOf(UserServiceUnavailableException.class);

    mockServer.verify();
  }

  @Test
  @DisplayName(
      "linkStoreToUser - Should send a POST /v1/users/stores request with userId and storeUid")
  void shouldSendPostRequestToLinkStoreToUser() {
    UUID userId = UUID.randomUUID();
    UUID storeUid = UUID.randomUUID();
    mockServer
        .expect(requestTo("http://mock-users-service/v1/users/stores"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.storeUid").value(storeUid.toString()))
        .andRespond(withSuccess());

    httpUserClient.linkStoreToUser(userId, storeUid);

    mockServer.verify();
  }

  @Test
  @DisplayName("getUserEmail - Should return trimmed email when user exists")
  void shouldReturnUserEmailWhenPresent() {
    UUID userId = UUID.randomUUID();
    mockServer
        .expect(requestTo("http://mock-users-service/v1/users/" + userId))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":\"" + userId + "\",\"email\":\"merchant@shoploc.fr\"}",
                MediaType.APPLICATION_JSON));

    Optional<String> email = httpUserClient.getUserEmail(userId);

    assertThat(email).contains("merchant@shoploc.fr");
    mockServer.verify();
  }
}
