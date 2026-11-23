package com.shoploc.store_service.client;

/** Interface defining client operations to communicate with the Users Service. */
public interface UserClient {

  /**
   * Checks whether an active user with the given ID exists in the Users Service.
   *
   * @param userId the identifier of the partner / user
   * @return true if the user exists, false otherwise
   */
  boolean userExists(Long userId);

  /**
   * Retrieves the email address of a user by their identifier.
   *
   * @param userId the identifier of the user
   * @return Optional containing the email if found, empty otherwise
   */
  java.util.Optional<String> getUserEmail(Long userId);

  /**
   * Notifies the Users Service to link an existing user to a newly created store.
   *
   * @param userId the identifier of the user
   * @param storeId the identifier of the newly created store
   */
  void linkStoreToUser(Long userId, Long storeId);

  /**
   * Notifies the Users Service to unlink a store from a merchant.
   *
   * @param userId the identifier of the former merchant
   * @param storeId the identifier of the store
   */
  void unlinkStoreFromUser(Long userId, Long storeId);
}
