package com.shoploc.store_service.exception;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ProductNotFoundException(UUID productUid) {
    super("Produit " + productUid + " introuvable dans le commerce");
  }

  public ProductNotFoundException(String identifier) {
    super("Produit " + identifier + " introuvable dans le commerce");
  }
}
