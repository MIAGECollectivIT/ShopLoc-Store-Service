package com.shoploc.store_service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  @DisplayName("ProductCreateRequest valide ne doit lever aucune violation")
  void shouldPassWhenProductCreateRequestIsValid() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-BAGUETTE-01",
            "Baguette Tradition",
            "Baguette croustillante au levain",
            new BigDecimal("1.20"),
            "https://cdn.shoploc.fr/tradition.jpg",
            25);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("ProductCreateRequest avec référence vide doit échouer")
  void shouldFailWhenReferenceIsBlank() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "   ",
            "Baguette Tradition",
            "Description",
            new BigDecimal("1.20"),
            null,
            10);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("reference"));
  }

  @Test
  @DisplayName("ProductCreateRequest avec nom vide doit échouer")
  void shouldFailWhenNameIsBlank() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-01",
            "",
            "Description",
            new BigDecimal("1.20"),
            null,
            10);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
  }

  @Test
  @DisplayName("ProductCreateRequest avec prix négatif doit échouer")
  void shouldFailWhenPriceIsNegative() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-01",
            "Produit",
            "Description",
            new BigDecimal("-5.00"),
            null,
            10);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
  }

  @Test
  @DisplayName("ProductCreateRequest avec stock négatif doit échouer")
  void shouldFailWhenStockIsNegative() {
    ProductCreateRequest request =
        new ProductCreateRequest(
            "REF-01",
            "Produit",
            "Description",
            new BigDecimal("5.00"),
            null,
            -3);

    Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stockQuantity"));
  }

  @Test
  @DisplayName("StockUpdateRequest avec nouvelle quantité négative doit échouer")
  void shouldFailWhenStockNewQuantityIsNegative() {
    StockUpdateRequest request = new StockUpdateRequest(null, -10);

    Set<ConstraintViolation<StockUpdateRequest>> violations = validator.validate(request);
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("newQuantity"));
  }
}
