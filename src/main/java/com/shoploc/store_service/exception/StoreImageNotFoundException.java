package com.shoploc.store_service.exception;

import java.util.UUID;

public class StoreImageNotFoundException extends ProductImageNotFoundException {

  private static final long serialVersionUID = 1L;
  private final String identifier;

  public StoreImageNotFoundException(String identifier) {
    super("Aucune image disponible pour le commerce : " + identifier);
    this.identifier = identifier;
  }

  public StoreImageNotFoundException(UUID storeUid) {
    this(storeUid != null ? storeUid.toString() : null);
  }

  @Override
  public String getMessage() {
    return "Aucune image disponible pour le commerce : " + identifier;
  }
}
