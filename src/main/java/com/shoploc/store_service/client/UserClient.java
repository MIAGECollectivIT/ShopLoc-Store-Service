package com.shoploc.store_service.client;

import java.util.Optional;
import java.util.UUID;

/** Interface defining client operations to communicate with the Users Service. */
public interface UserClient {

  /**
   * Checks whether an active user with the given UUID exists in the Users Service.
   *
   * @param userId the UUID identifier of the partner / user
   * @return true if the user exists, false otherwise
   */
  boolean userExists(UUID userId);

  /**
   * Retrieves the email address of a user by their UUID identifier.
   *
   * @param userId the UUID identifier of the user
   * @return Optional containing the email if found, empty otherwise
   */
  Optional<String> getUserEmail(UUID userId);

  /**
   * Notifies the Users Service to link an existing user to a newly created store.
   *
   * @param userId the UUID identifier of the user
   * @param storeUid the UUID identifier of the newly created store
   */
  void linkStoreToUser(UUID userId, UUID storeUid);

  /**
   * Notifies the Users Service to unlink a store from a merchant.
   *
   * @param userId the UUID identifier of the former merchant
   * @param storeUid the UUID identifier of the store
   */
  void unlinkStoreFromUser(UUID userId, UUID storeUid);
}
