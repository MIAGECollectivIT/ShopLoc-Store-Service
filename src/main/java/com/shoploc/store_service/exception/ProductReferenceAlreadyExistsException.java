package com.shoploc.store_service.exception;

public class ProductReferenceAlreadyExistsException extends RuntimeException {

  public ProductReferenceAlreadyExistsException(String reference, Long storeId) {
    super(
        "Un produit avec la référence '" + reference + "' existe déjà dans le commerce " + storeId);
  }
}
