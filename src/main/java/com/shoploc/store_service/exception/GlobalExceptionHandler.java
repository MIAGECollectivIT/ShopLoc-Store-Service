package com.shoploc.store_service.exception;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Gestionnaire d'erreurs global conforme à la RFC 7807 (ProblemDetail). Maintient également les
 * propriétés 'error', 'message', 'timestamp' pour rétro-compatibilité client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String PROBLEM_BASE_URL = "https://errors.shoploc.com/";

  private ProblemDetail createProblem(HttpStatus status, String type, String message) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
    problem.setType(URI.create(PROBLEM_BASE_URL + type));
    problem.setProperty("timestamp", Instant.now().toString());
    problem.setProperty("error", status.getReasonPhrase());
    problem.setProperty("message", message);
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(error.getField(), error.getDefaultMessage());
    }

    ProblemDetail problem =
        createProblem(
            HttpStatus.BAD_REQUEST, "validation-error", "Validation des données d'entrée échouée");
    problem.setProperty("fieldErrors", fieldErrors);
    problem.setProperty("invalid_params", fieldErrors);
    return problem;
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
    String paramName = ex.getName();
    String requiredType =
        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valide";
    String message =
        String.format("Paramètre '%s' invalide : format %s attendu", paramName, requiredType);
    return createProblem(HttpStatus.BAD_REQUEST, "type-mismatch", message);
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ProblemDetail handleUserNotFoundException(UserNotFoundException ex) {
    ProblemDetail problem = createProblem(HttpStatus.NOT_FOUND, "user-not-found", ex.getMessage());
    problem.setProperty("userId", ex.getUserId());
    return problem;
  }

  @ExceptionHandler(UserServiceUnavailableException.class)
  public ProblemDetail handleUserServiceUnavailableException(UserServiceUnavailableException ex) {
    return createProblem(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", ex.getMessage());
  }

  @ExceptionHandler(InvalidFileException.class)
  public ProblemDetail handleInvalidFileException(InvalidFileException ex) {
    return createProblem(HttpStatus.BAD_REQUEST, "invalid-file", ex.getMessage());
  }

  @ExceptionHandler(FileStorageException.class)
  public ProblemDetail handleFileStorageException(FileStorageException ex) {
    return createProblem(HttpStatus.INTERNAL_SERVER_ERROR, "file-storage-error", ex.getMessage());
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ProblemDetail handleUnauthorizedException(UnauthorizedException ex) {
    return createProblem(HttpStatus.UNAUTHORIZED, "unauthorized", ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
    return createProblem(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage());
  }

  @ExceptionHandler({
    ProductNotFoundException.class,
    StoreNotFoundException.class,
    ProductImageNotFoundException.class
  })
  public ProblemDetail handleNotFoundExceptions(RuntimeException ex) {
    return createProblem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
  }

  @ExceptionHandler(ProductReferenceAlreadyExistsException.class)
  public ProblemDetail handleProductReferenceConflict(ProductReferenceAlreadyExistsException ex) {
    return createProblem(HttpStatus.CONFLICT, "product-reference-conflict", ex.getMessage());
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ProblemDetail handleInsufficientStockException(InsufficientStockException ex) {
    return createProblem(HttpStatus.BAD_REQUEST, "insufficient-stock", ex.getMessage());
  }

  @ExceptionHandler(StoreAlreadyRevokedException.class)
  public ProblemDetail handleStoreAlreadyRevokedException(StoreAlreadyRevokedException ex) {
    return createProblem(HttpStatus.CONFLICT, "store-already-revoked", ex.getMessage());
  }

  @ExceptionHandler(StoreNotRevokedException.class)
  public ProblemDetail handleStoreNotRevokedException(StoreNotRevokedException ex) {
    return createProblem(HttpStatus.CONFLICT, "store-not-revoked", ex.getMessage());
  }

  @ExceptionHandler(RevokedStoreException.class)
  public ProblemDetail handleRevokedStoreException(RevokedStoreException ex) {
    return createProblem(HttpStatus.UNPROCESSABLE_ENTITY, "revoked-store", ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
    return createProblem(HttpStatus.BAD_REQUEST, "illegal-argument", ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleIllegalStateException(IllegalStateException ex) {
    return createProblem(HttpStatus.CONFLICT, "illegal-state", ex.getMessage());
  }
}
