package com.codeops.courier.repository;

import com.codeops.courier.entity.MergeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MergeRequestRepository extends JpaRepository<MergeRequest, UUID> {

    List<MergeRequest> findByTargetCollectionId(UUID collectionId);

    List<MergeRequest> findBySourceForkId(UUID forkId);

    List<MergeRequest> findByTargetCollectionIdAndStatus(UUID collectionId, String status);

    List<MergeRequest> findByRequestedByUserId(UUID userId);
}
