package com.shoploc.store_service.exception;

public class ProductReferenceAlreadyExistsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ProductReferenceAlreadyExistsException(String reference, Long storeId) {
    super(
        "Un produit avec la référence '" + reference + "' existe déjà dans le commerce " + storeId);
  }
}
