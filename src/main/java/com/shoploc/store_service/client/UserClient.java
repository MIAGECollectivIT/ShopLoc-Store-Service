package com.shoploc.store_service.client;

/**
 * Interface defining client operations to communicate with the Users Service.
 */
public interface UserClient {

    /**
     * Checks whether an active user with the given ID exists in the Users Service.
     *
     * @param userId the identifier of the partner / user
     * @return true if the user exists, false otherwise
     */
    boolean userExists(Long userId);

    /**
     * Notifies the Users Service to link an existing user to a newly created store.
     *
     * @param userId the identifier of the user
     * @param storeId the identifier of the newly created store
     */
    void linkStoreToUser(Long userId, Long storeId);
}
