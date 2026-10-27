package com.shoploc.store_service;

import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/stores")
@Tag(name = "Stores", description = "Gestion des commerces")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    @Operation(summary = "Récupère tous les commerces")
    public List<StoreResponse> getAllStores(@RequestParam(required = false) Long userId) {
        List<Store> stores;
        if (userId != null) {
            stores = storeService.getStoresByUserId(userId);
        } else {
            stores = storeService.getAllStores();
        }

        List<StoreResponse> responseList = new ArrayList<>();
        for (Store store : stores) {
            responseList.add(StoreResponse.fromEntity(store));
        }
        return responseList;
    }

    @GetMapping("/{storeId}")
    @Operation(summary = "Récupère un commerce par son identifiant")
    public ResponseEntity<StoreResponse> getStoreById(@PathVariable Long storeId) {
        Optional<Store> storeOptional = storeService.getStoreById(storeId);
        if (storeOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Store store = storeOptional.get();
        StoreResponse response = StoreResponse.fromEntity(store);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Créer un nouveau commerce")
    public ResponseEntity<StoreResponse> createStore(@Valid @RequestBody StoreCreateRequest request) {
        Store createdStore = storeService.createStore(request);
        StoreResponse response = StoreResponse.fromEntity(createdStore);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}