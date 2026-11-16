package com.shoploc.store_service.exception;

public class StoreNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StoreNotFoundException(String identifier) {
    super("Commerce introuvable avec l'identifiant : " + identifier);
  }

  public StoreNotFoundException(Long storeId) {
    this(String.valueOf(storeId));
  }
}
