package com.shoploc.store_service;

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

    // GET http://localhost:8080/V1/stores
    @GetMapping
    public List<Store> getAllStores() {
        return storeService.getAllStores();
    }

    // GET http://localhost:8080/V1/stores/{storeId}
    @GetMapping("/{storeId}")
    public ResponseEntity<Store> getStoreById(@PathVariable Long storeId) {
        return storeService.getStoreById(storeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST http://localhost:8080/V1/stores
    @PostMapping
    public ResponseEntity<Store> createStore(@RequestBody Store store) {
        Store createdStore = storeService.createStore(store);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdStore);
    }
}