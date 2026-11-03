package com.shoploc.store_service;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

  List<Product> findByStoreId(Long storeId);

  Optional<Product> findByIdAndStoreId(Long id, Long storeId);

  boolean existsByStoreIdAndReference(Long storeId, String reference);

  boolean existsByStoreIdAndReferenceAndIdNot(Long storeId, String reference, Long id);
}
