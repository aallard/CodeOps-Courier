package com.codeops.courier.repository;

import com.codeops.courier.entity.CollectionShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionShareRepository extends JpaRepository<CollectionShare, UUID> {

    List<CollectionShare> findByCollectionId(UUID collectionId);

    List<CollectionShare> findBySharedWithUserId(UUID userId);

    Optional<CollectionShare> findByCollectionIdAndSharedWithUserId(UUID collectionId, UUID userId);

    boolean existsByCollectionIdAndSharedWithUserId(UUID collectionId, UUID userId);

    void deleteByCollectionIdAndSharedWithUserId(UUID collectionId, UUID userId);
}
