package com.shoploc.store_service.dto;

import com.shoploc.store_service.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ProductResponse {

  private UUID uid;
  private UUID storeUid;
  private String reference;
  private String name;
  private String description;
  private BigDecimal price;
  private String imageUrl;
  private Integer stockQuantity;
  private Integer alertThreshold;
  private Boolean isAvailable;
  private Instant createdAt;
  private Instant updatedAt;

  public ProductResponse() {}

  public ProductResponse(
      UUID uid,
      UUID storeUid,
      String reference,
      String name,
      String description,
      BigDecimal price,
      String imageUrl,
      Integer stockQuantity,
      Instant createdAt,
      Instant updatedAt) {
    this(
        uid,
        storeUid,
        reference,
        name,
        description,
        price,
        imageUrl,
        stockQuantity,
        5,
        stockQuantity != null && stockQuantity > 0,
        createdAt,
        updatedAt);
  }

  public ProductResponse(
      UUID uid,
      UUID storeUid,
      String reference,
      String name,
      String description,
      BigDecimal price,
      String imageUrl,
      Integer stockQuantity,
      Integer alertThreshold,
      Boolean isAvailable,
      Instant createdAt,
      Instant updatedAt) {
    this.uid = uid;
    this.storeUid = storeUid;
    this.reference = reference;
    this.name = name;
    this.description = description;
    this.price = price;
    this.imageUrl = imageUrl;
    this.stockQuantity = stockQuantity;
    this.alertThreshold = alertThreshold != null ? alertThreshold : 5;
    this.isAvailable =
        isAvailable != null ? isAvailable : (stockQuantity != null && stockQuantity > 0);
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static ProductResponse fromEntity(Product product, UUID storeUid) {
    if (product == null) {
      return null;
    }
    return new ProductResponse(
        product.getUid(),
        storeUid,
        product.getReference(),
        product.getName(),
        product.getDescription(),
        product.getPrice(),
        product.getImageUrl(),
        product.getStockQuantity(),
        product.getAlertThreshold() != null ? product.getAlertThreshold() : 5,
        product.isAvailable(),
        product.getCreatedAt(),
        product.getUpdatedAt());
  }

  public UUID getUid() {
    return uid;
  }

  public void setUid(UUID uid) {
    this.uid = uid;
  }

  public UUID getStoreUid() {
    return storeUid;
  }

  public void setStoreUid(UUID storeUid) {
    this.storeUid = storeUid;
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

  public Integer getAlertThreshold() {
    return alertThreshold;
  }

  public void setAlertThreshold(Integer alertThreshold) {
    this.alertThreshold = alertThreshold;
  }

  public Boolean getIsAvailable() {
    return isAvailable;
  }

  public void setIsAvailable(Boolean isAvailable) {
    this.isAvailable = isAvailable;
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
