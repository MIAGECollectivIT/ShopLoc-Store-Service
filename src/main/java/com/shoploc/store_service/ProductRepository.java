package com.shoploc.store_service;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

  List<Product> findByStoreId(Long storeId);

  Optional<Product> findByIdAndStoreId(Long id, Long storeId);

  /**
   * Locks the product row in the database (SELECT ... FOR UPDATE) to serialize concurrent stock
   * modifications and prevent overselling risks.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.id = :id AND p.storeId = :storeId")
  Optional<Product> findByIdAndStoreIdForUpdate(
      @Param("id") Long id, @Param("storeId") Long storeId);

  boolean existsByStoreIdAndReference(Long storeId, String reference);

  boolean existsByStoreIdAndReferenceAndIdNot(Long storeId, String reference, Long id);
}
