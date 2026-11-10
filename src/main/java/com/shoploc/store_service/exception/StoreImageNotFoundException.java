package com.shoploc.store_service.exception;

public class StoreImageNotFoundException extends ProductImageNotFoundException {

  private static final long serialVersionUID = 1L;
  private final Long storeId;

  public StoreImageNotFoundException(Long storeId) {
    super(storeId, 0L);
    this.storeId = storeId;
  }

  @Override
  public String getMessage() {
    return "Aucune image disponible pour le commerce id=" + storeId;
  }
}
