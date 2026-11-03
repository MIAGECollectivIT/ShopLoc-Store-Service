package com.shoploc.store_service.exception;

public class ProductNotFoundException extends RuntimeException {

  public ProductNotFoundException(Long productId) {
    super("Produit introuvable avec l'identifiant : " + productId);
  }

  public ProductNotFoundException(Long storeId, Long productId) {
    super("Produit " + productId + " introuvable dans le commerce " + storeId);
  }
}
