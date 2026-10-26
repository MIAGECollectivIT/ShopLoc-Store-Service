package com.shoploc.store_service.dto;

import com.shoploc.store_service.Store;
import com.shoploc.store_service.User;
import com.shoploc.store_service.validation.RibIbanHelper;
import com.shoploc.store_service.validation.ValidRibOrIban;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class StoreCreateRequest {

    @NotBlank(message = "Le nom de l'établissement est obligatoire")
    @Size(max = 255, message = "Le nom ne doit pas dépasser 255 caractères")
    private String name;

    @NotBlank(message = "L'adresse de l'établissement est obligatoire")
    @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères")
    private String address;

    @Size(max = 500, message = "L'URL ne doit pas dépasser 500 caractères")
    private String url;

    @NotBlank(message = "La catégorie d'activité est obligatoire")
    @Size(max = 255, message = "La catégorie ne doit pas dépasser 255 caractères")
    private String category;

    @NotBlank(message = "Le RIB ou IBAN est obligatoire")
    @ValidRibOrIban(message = "Le format ou la clé algorithmique du RIB/IBAN est invalide")
    private String rib;

    @NotNull(message = "L'identifiant du partenaire propriétaire est obligatoire")
    @Positive(message = "L'identifiant du partenaire propriétaire doit être un identifiant valide")
    private Long userId;

    public StoreCreateRequest() {
    }

    public StoreCreateRequest(String name, String address, String url, String category, String rib, Long userId) {
        this.name = name;
        this.address = address;
        this.url = url;
        this.category = category;
        this.rib = rib;
        this.userId = userId;
    }

    public Store toEntity() {
        return new Store(
                name != null ? name.trim() : null,
                address != null ? address.trim() : null,
                url != null ? url.trim() : null,
                category != null ? category.trim() : null,
                RibIbanHelper.clean(rib),
                userId != null ? new User(userId) : null
        );
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
