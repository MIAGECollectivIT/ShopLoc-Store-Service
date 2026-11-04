package com.shoploc.store_service.exception;

public class ProductImageNotFoundException extends RuntimeException {

  public ProductImageNotFoundException(Long storeId, Long productId) {
    super("Aucune image disponible pour le produit id=" + productId + " dans le magasin id=" + storeId);
  }
}

