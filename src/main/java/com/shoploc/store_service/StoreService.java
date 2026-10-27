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
        User associatedUser = store.getUser();
        if (associatedUser != null && associatedUser.getId() != null) {
            Long userId = associatedUser.getId();
            User persistentUser = resolveOrCreateUser(userId);
            store.setUser(persistentUser);
        }
        return storeRepository.save(store);
    }

    private User resolveOrCreateUser(Long userId) {
        Optional<User> existingUser = userRepository.findById(userId);
        if (existingUser.isPresent()) {
            return existingUser.get();
        }
        User newUser = new User(userId);
        return userRepository.save(newUser);
    }
}