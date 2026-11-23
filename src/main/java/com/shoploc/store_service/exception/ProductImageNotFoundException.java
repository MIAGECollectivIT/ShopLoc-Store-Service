package com.shoploc.store_service.exception;

import java.util.UUID;

public class ProductImageNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ProductImageNotFoundException(UUID productUid) {
    super("Aucune image disponible pour le produit uid=" + productUid);
  }

  protected ProductImageNotFoundException(String message) {
    super(message);
  }
}
