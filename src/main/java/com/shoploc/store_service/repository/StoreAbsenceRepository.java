package com.shoploc.store_service.repository;

import com.shoploc.store_service.entity.StoreAbsence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreAbsenceRepository extends JpaRepository<StoreAbsence, Long> {

  Optional<StoreAbsence> findByUid(UUID uid);

  Optional<StoreAbsence> findByStoreIdAndUid(Long storeId, UUID uid);
}
