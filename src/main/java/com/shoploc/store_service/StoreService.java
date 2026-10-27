package com.shoploc.store_service;

import com.shoploc.store_service.client.UserClient;
import com.shoploc.store_service.dto.StoreCreateRequest;
import com.shoploc.store_service.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserClient userClient;

    public StoreService(StoreRepository storeRepository, UserClient userClient) {
        this.storeRepository = storeRepository;
        this.userClient = userClient;
    }

    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }

    public Optional<Store> getStoreById(Long id) {
        return storeRepository.findById(id);
    }

    public List<Store> getStoresByUserId(Long userId) {
        return storeRepository.findByUserId(userId);
    }

    public Store createStore(StoreCreateRequest request) {
        Long userId = request.getUserId();
        if (!userClient.userExists(userId)) {
            throw new UserNotFoundException(userId);
        }

        Store storeToSave = request.toEntity();
        Store savedStore = storeRepository.save(storeToSave);

        userClient.linkStoreToUser(userId, savedStore.getId());

        return savedStore;
    }

    public Store createStore(Store store) {
        Long userId = store.getUserId();
        if (userId != null && !userClient.userExists(userId)) {
            throw new UserNotFoundException(userId);
        }

        Store savedStore = storeRepository.save(store);
        if (userId != null) {
            userClient.linkStoreToUser(userId, savedStore.getId());
        }
        return savedStore;
    }
}