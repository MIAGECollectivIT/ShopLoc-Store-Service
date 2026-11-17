package com.shoploc.store_service.exception;

/**
 * Thrown when an administrative attempt is made to revoke a store that is already revoked
 * (EXNF-004).
 */
public class StoreAlreadyRevokedException extends RuntimeException {
  public StoreAlreadyRevokedException(String identifier) {
    super("Le commerce '" + identifier + "' est déjà révoqué et ne peut être révoqué à nouveau.");
  }
}
