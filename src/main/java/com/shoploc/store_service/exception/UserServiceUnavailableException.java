package com.shoploc.store_service.exception;

public class UserServiceUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UserServiceUnavailableException(String message) {
    super(message);
  }

  public UserServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
