package com.shoploc.store_service.dto;

import com.shoploc.store_service.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class ProductCreateRequest {

  @NotBlank(message = "La référence de l'article est obligatoire")
  @Size(max = 100, message = "La référence ne doit pas dépasser 100 caractères")
  private String reference;

  @NotBlank(message = "Le nom du produit est obligatoire")
  @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères")
  private String name;

  private String description;

  @NotNull(message = "Le prix du produit est obligatoire")
  @DecimalMin(value = "0.00", message = "Le prix doit être positif ou nul")
  private BigDecimal price;

  @Size(max = 1000, message = "L'URL de l'image ne doit pas dépasser 1000 caractères")
  private String imageUrl;

  @Min(value = 0, message = "Le stock ne peut pas être négatif")
  private Integer stockQuantity;

  public ProductCreateRequest() {}

  public ProductCreateRequest(
      String reference,
      String name,
      String description,
      BigDecimal price,
      String imageUrl,
      Integer stockQuantity) {
    this.reference = reference;
    this.name = name;
    this.description = description;
    this.price = price;
    this.imageUrl = imageUrl;
    this.stockQuantity = stockQuantity;
  }

  public Product toEntity(Long storeId) {
    return new Product(storeId, reference, name, description, price, imageUrl, stockQuantity);
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public Integer getStockQuantity() {
    return stockQuantity;
  }

  public void setStockQuantity(Integer stockQuantity) {
    this.stockQuantity = stockQuantity;
  }
}
