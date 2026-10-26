package com.shoploc.store_service.dto;

import com.shoploc.store_service.Store;

public class StoreResponse {

    private Long id;
    private String name;
    private String address;
    private String url;
    private String category;
    private String rib;
    private Long userId;

    public StoreResponse() {
    }

    public StoreResponse(Long id, String name, String address, String url, String category, String rib, Long userId) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.url = url;
        this.category = category;
        this.rib = rib;
        this.userId = userId;
    }

    public static StoreResponse fromEntity(Store store) {
        if (store == null) {
            return null;
        }
        return new StoreResponse(
                store.getId(),
                store.getName(),
                store.getAddress(),
                store.getUrl(),
                store.getCategory(),
                store.getRib(),
                store.getUserId()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
