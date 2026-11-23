package com.shoploc.store_service.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final UUID userId;

  public UserNotFoundException(UUID userId) {
    super(String.format("Le partenaire avec l'identifiant %s n'existe pas", userId));
    this.userId = userId;
  }

  public UUID getUserId() {
    return userId;
  }
}
