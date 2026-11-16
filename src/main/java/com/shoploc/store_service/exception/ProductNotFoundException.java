package com.shoploc.store_service.exception;

public class ProductNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ProductNotFoundException(Long productId) {
    super("Produit " + productId + " introuvable dans le commerce");
  }
}
