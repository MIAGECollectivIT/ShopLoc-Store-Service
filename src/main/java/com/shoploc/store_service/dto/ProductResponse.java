package com.shoploc.store_service.dto;

import com.shoploc.store_service.Product;
import java.math.BigDecimal;
import java.time.Instant;

public class ProductResponse {

  private Long id;
  private Long storeId;
  private String reference;
  private String name;
  private String description;
  private BigDecimal price;
  private String imageUrl;
  private Integer stockQuantity;
  private Instant createdAt;
  private Instant updatedAt;

  public ProductResponse() {}

  public ProductResponse(
      Long id,
      Long storeId,
      String reference,
      String name,
      String description,
      BigDecimal price,
      String imageUrl,
      Integer stockQuantity,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.storeId = storeId;
    this.reference = reference;
    this.name = name;
    this.description = description;
    this.price = price;
    this.imageUrl = imageUrl;
    this.stockQuantity = stockQuantity;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static ProductResponse fromEntity(Product product) {
    if (product == null) {
      return null;
    }
    return new ProductResponse(
        product.getId(),
        product.getStoreId(),
        product.getReference(),
        product.getName(),
        product.getDescription(),
        product.getPrice(),
        product.getImageUrl(),
        product.getStockQuantity(),
        product.getCreatedAt(),
        product.getUpdatedAt());
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getStoreId() {
    return storeId;
  }

  public void setStoreId(Long storeId) {
    this.storeId = storeId;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}

