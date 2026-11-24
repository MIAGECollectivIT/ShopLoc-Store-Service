package com.shoploc.store_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "products",
    uniqueConstraints = {
      @jakarta.persistence.UniqueConstraint(
          name = "uq_store_product_reference",
          columnNames = {"store_id", "reference"})
    })
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uid", nullable = false, unique = true, updatable = false)
  private UUID uid;

  @Column(name = "store_id", nullable = false)
  private Long storeId;

  @Column(nullable = false, length = 100)
  private String reference;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(name = "image_url", length = 1000)
  private String imageUrl;

  @Column(name = "stock_quantity", nullable = false)
  private Integer stockQuantity;

  @Column(name = "alert_threshold", nullable = false)
  private Integer alertThreshold;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Product() {
    this.uid = UUID.randomUUID();
  }

  public Product(
      Long storeId,
      String reference,
      String name,
      String description,
      BigDecimal price,
      String imageUrl,
      Integer stockQuantity) {
    this(storeId, reference, name, description, price, imageUrl, stockQuantity, 5);
  }

  public Product(
      Long storeId,
      String reference,
      String name,
      String description,
      BigDecimal price,
      String imageUrl,
      Integer stockQuantity,
      Integer alertThreshold) {
    this();
    this.storeId = storeId;
    this.reference = reference;
    this.name = name;
    this.description = description;
    this.price = price;
    this.imageUrl = imageUrl;
    this.stockQuantity = stockQuantity != null ? stockQuantity : 0;
    this.alertThreshold = alertThreshold != null ? alertThreshold : 5;
  }

  @PrePersist
  protected void onCreate() {
    if (this.uid == null) {
      this.uid = UUID.randomUUID();
    }
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.stockQuantity == null) {
      this.stockQuantity = 0;
    }
    if (this.alertThreshold == null) {
      this.alertThreshold = 5;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public UUID getUid() {
    return uid;
  }

  public void setUid(UUID uid) {
    this.uid = uid;
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

  public Integer getAlertThreshold() {
    return alertThreshold;
  }

  public void setAlertThreshold(Integer alertThreshold) {
    this.alertThreshold = alertThreshold;
  }

  public boolean isAvailable() {
    return stockQuantity != null && stockQuantity > 0;
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
