package com.shoploc.store_service.exception;

public class StoreImageNotFoundException extends ProductImageNotFoundException {

  private static final long serialVersionUID = 1L;
  private final String identifier;

  public StoreImageNotFoundException(String identifier) {
    super(0L);
    this.identifier = identifier;
  }

  @Override
  public String getMessage() {
    return "Aucune image disponible pour le commerce : " + identifier;
  }
}
