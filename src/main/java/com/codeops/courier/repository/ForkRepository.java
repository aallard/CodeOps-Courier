package com.codeops.courier.repository;

import com.codeops.courier.entity.Fork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForkRepository extends JpaRepository<Fork, UUID> {

    List<Fork> findBySourceCollectionId(UUID collectionId);

    Optional<Fork> findByForkedCollectionId(UUID collectionId);

    List<Fork> findByForkedByUserId(UUID userId);

    boolean existsBySourceCollectionIdAndForkedByUserId(UUID collectionId, UUID userId);
}
