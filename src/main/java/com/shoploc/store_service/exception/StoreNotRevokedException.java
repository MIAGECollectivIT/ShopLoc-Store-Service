package com.shoploc.store_service.exception;

/**
 * Thrown when an administrative attempt is made to reactivate a store that is not currently revoked
 * (EXNF-013).
 */
public class StoreNotRevokedException extends RuntimeException {
  public StoreNotRevokedException(String identifier) {
    super("Le commerce '" + identifier + "' n'est pas révoqué et ne peut être réactivé.");
  }
}
