package com.shoploc.store_service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  @DisplayName("Should validate a complete and valid request")
  void shouldValidateValidRequest() {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Le Pain Doré",
            "10 rue Nationale, 59000 Lille",
            "https://example.com/shop.jpg",
            "Boulangerie",
            "FR7630006000011234567890189",
            1L);

    Set<ConstraintViolation<StoreCreateRequest>> violations = validator.validate(request);
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("Should reject blank name")
  void shouldRejectBlankName() {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "", "10 rue Nationale", null, "Boulangerie", "FR7630006000011234567890189", 1L);

    Set<ConstraintViolation<StoreCreateRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  @Test
  @DisplayName("Should reject blank category")
  void shouldRejectBlankCategory() {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Mon Magasin", "10 rue Nationale", null, "   ", "FR7630006000011234567890189", 1L);

    Set<ConstraintViolation<StoreCreateRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
  }

  @Test
  @DisplayName("Should reject algorithmically invalid RIB")
  void shouldRejectInvalidRib() {
    StoreCreateRequest request =
        new StoreCreateRequest(
            "Mon Magasin",
            "10 rue Nationale",
            null,
            "Boulangerie",
            "FR7630006000011234567890188", // Invalid check digits
            1L);

    Set<ConstraintViolation<StoreCreateRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rib"));
  }

  @Test
  @DisplayName("Should allow null userId (injected from JWT) but reject negative values")
  void shouldAllowNullUserIdAndRejectNegative() {
    StoreCreateRequest requestNullUser =
        new StoreCreateRequest(
            "Mon Magasin",
            "10 rue Nationale",
            null,
            "Boulangerie",
            "FR7630006000011234567890189",
            null);

    Set<ConstraintViolation<StoreCreateRequest>> violations = validator.validate(requestNullUser);
    assertThat(violations).isEmpty();

    StoreCreateRequest requestNegativeUser =
        new StoreCreateRequest(
            "Mon Magasin",
            "10 rue Nationale",
            null,
            "Boulangerie",
            "FR7630006000011234567890189",
            -5L);

    Set<ConstraintViolation<StoreCreateRequest>> violationsNeg =
        validator.validate(requestNegativeUser);
    assertThat(violationsNeg).anyMatch(v -> v.getPropertyPath().toString().equals("userId"));
  }
}
