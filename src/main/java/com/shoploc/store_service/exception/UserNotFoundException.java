package com.shoploc.store_service.exception;

public class UserNotFoundException extends RuntimeException {

    private final Long userId;

    public UserNotFoundException(Long userId) {
        super(String.format("Le partenaire avec l'identifiant %d n'existe pas", userId));
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
