package com.shoploc.store_service.exception;

public class ProductNotFoundException extends RuntimeException {

  public ProductNotFoundException(Long storeId, Long productId) {
    super("Produit " + productId + " introuvable dans le commerce " + storeId);
  }
}
