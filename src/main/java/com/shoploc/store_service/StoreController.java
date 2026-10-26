package com.shoploc.store_service;

import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.dto.StoreResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    // GET /v1/stores ou /v1/stores?userId={userId}
    @GetMapping
    public List<StoreResponse> getAllStores(@RequestParam(required = false) Long userId) {
        List<Store> stores = (userId != null)
                ? storeService.getStoresByUserId(userId)
                : storeService.getAllStores();
        return stores.stream().map(StoreResponse::fromEntity).toList();
    }

    // GET /v1/stores/{storeId}
    @GetMapping("/{storeId}")
    public ResponseEntity<StoreResponse> getStoreById(@PathVariable Long storeId) {
        return storeService.getStoreById(storeId)
                .map(StoreResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /v1/stores
    @PostMapping
    public ResponseEntity<StoreResponse> createStore(@Valid @RequestBody StoreCreateRequest request) {
        Store createdStore = storeService.createStore(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(StoreResponse.fromEntity(createdStore));
    }
}