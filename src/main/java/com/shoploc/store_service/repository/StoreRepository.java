package com.shoploc.store_service.repository;

import com.shoploc.store_service.entity.Store;
import com.shoploc.store_service.entity.StoreStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreRepository
    extends JpaRepository<Store, Long>, JpaSpecificationExecutor<Store> {

  /** Absences are lazy by default; every read that returns stores loads them in one query. */
  String WITH_ABSENCES = "absences";

  @Override
  @EntityGraph(attributePaths = WITH_ABSENCES)
  List<Store> findAll();

  @Override
  @EntityGraph(attributePaths = WITH_ABSENCES)
  List<Store> findAll(Specification<Store> spec);

  @EntityGraph(attributePaths = WITH_ABSENCES)
  List<Store> findByOwnerUid(UUID ownerUid);

  @EntityGraph(attributePaths = WITH_ABSENCES)
  List<Store> findByStatus(StoreStatus status);

  @EntityGraph(attributePaths = WITH_ABSENCES)
  List<Store> findByOwnerUidAndStatus(UUID ownerUid, StoreStatus status);

  @EntityGraph(attributePaths = WITH_ABSENCES)
  Optional<Store> findByUid(UUID uid);

  @EntityGraph(attributePaths = WITH_ABSENCES)
  List<Store> findByUidIn(List<UUID> uids);

  boolean existsByUid(UUID uid);
}
