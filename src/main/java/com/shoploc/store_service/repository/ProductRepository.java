package com.shoploc.store_service.repository;

import com.shoploc.store_service.entity.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

  List<Product> findByStoreId(Long storeId);

  Optional<Product> findByUid(UUID uid);

  Optional<Product> findByStoreIdAndUid(Long storeId, UUID uid);

  /**
   * Locks the product row in the database (SELECT ... FOR UPDATE) to serialize concurrent stock
   * modifications and prevent overselling risks.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.storeId = :storeId AND p.uid = :uid")
  Optional<Product> findByStoreIdAndUidForUpdate(
      @Param("storeId") Long storeId, @Param("uid") UUID uid);

  boolean existsByStoreIdAndReference(Long storeId, String reference);

  boolean existsByStoreIdAndReferenceAndUidNot(Long storeId, String reference, UUID uid);
}
