package com.shoploc.store_service.exception;

import java.util.UUID;

public class StoreNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StoreNotFoundException(String identifier) {
    super("Commerce introuvable avec l'identifiant : " + identifier);
  }

  public StoreNotFoundException(UUID storeUid) {
    this(String.valueOf(storeUid));
  }
}
