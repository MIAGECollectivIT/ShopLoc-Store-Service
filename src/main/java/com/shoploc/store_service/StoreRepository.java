package com.shoploc.store_service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

  List<Store> findByUserId(Long userId);

  Optional<Store> findByUid(UUID uid);

  boolean existsByUid(UUID uid);
}
