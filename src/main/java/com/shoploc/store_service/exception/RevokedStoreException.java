package com.shoploc.store_service.exception;

/**
 * Thrown when an operation (product creation, edit, stock reservation) is attempted on a revoked
 * store (EXNF-005, RMT-003).
 */
public class RevokedStoreException extends RuntimeException {
  public RevokedStoreException(String identifier) {
    super(
        "Opération impossible : le commerce '"
            + identifier
            + "' a été révoqué du programme ShopLoc.");
  }
}
