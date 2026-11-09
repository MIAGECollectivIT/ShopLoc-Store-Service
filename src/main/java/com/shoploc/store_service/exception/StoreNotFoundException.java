package com.shoploc.store_service.exception;

public class StoreNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StoreNotFoundException(Long storeId) {
    super("Commerce introuvable avec l'identifiant : " + storeId);
  }
}
