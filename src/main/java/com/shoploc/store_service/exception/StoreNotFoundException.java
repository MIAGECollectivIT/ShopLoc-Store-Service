package com.shoploc.store_service.exception;

public class StoreNotFoundException extends RuntimeException {

  public StoreNotFoundException(Long storeId) {
    super("Commerce introuvable avec l'identifiant : " + storeId);
  }
}
