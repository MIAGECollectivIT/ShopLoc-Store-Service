package com.shoploc.store_service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "shops")
public class Store {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uid", nullable = false, unique = true)
  private UUID uid;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false, length = 255)
  private String address;

  @Column(length = 500)
  private String url;

  @Column(nullable = false, length = 255)
  private String category;

  @Column(nullable = false, length = 34)
  private String rib;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  public Store() {
    this.uid = UUID.randomUUID();
  }

  public Store(String name, String address, String url, String category, String rib, Long userId) {
    this();
    this.name = name;
    this.address = address;
    this.url = url;
    this.category = category;
    this.rib = rib;
    this.userId = userId;
  }

  public Store(
      UUID uid,
      String name,
      String address,
      String url,
      String category,
      String rib,
      Long userId) {
    this.uid = uid != null ? uid : UUID.randomUUID();
    this.name = name;
    this.address = address;
    this.url = url;
    this.category = category;
    this.rib = rib;
    this.userId = userId;
  }

  @PrePersist
  public void prePersist() {
    if (this.uid == null) {
      this.uid = UUID.randomUUID();
    }
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getRib() {
    return rib;
  }

  public void setRib(String rib) {
    this.rib = rib;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }
}
