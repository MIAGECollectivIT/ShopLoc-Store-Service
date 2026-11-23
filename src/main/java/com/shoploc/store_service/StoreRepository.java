package com.shoploc.store_service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreRepository
    extends JpaRepository<Store, Long>, JpaSpecificationExecutor<Store> {

  List<Store> findByOwnerUid(UUID ownerUid);

  List<Store> findByStatus(StoreStatus status);

  List<Store> findByOwnerUidAndStatus(UUID ownerUid, StoreStatus status);

  Optional<Store> findByUid(UUID uid);

  boolean existsByUid(UUID uid);
}
