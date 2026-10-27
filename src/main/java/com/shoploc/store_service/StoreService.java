package com.shoploc.store_service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    public StoreService(StoreRepository storeRepository, UserRepository userRepository) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
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

    public Store createStore(Store store) {
        if (store.getUser() != null && store.getUser().getId() != null) {
            User user = userRepository.findById(store.getUser().getId())
                    .orElseGet(() -> userRepository.save(new User(store.getUser().getId())));
            store.setUser(user);
        }
        return storeRepository.save(store);
    }
}