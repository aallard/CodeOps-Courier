package com.codeops.courier.repository;

import com.codeops.courier.entity.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    List<Collection> findByTeamId(UUID teamId);

    Optional<Collection> findByTeamIdAndName(UUID teamId, String name);

    List<Collection> findByTeamIdAndIsSharedTrue(UUID teamId);

    List<Collection> findByCreatedBy(UUID userId);

    boolean existsByTeamIdAndName(UUID teamId, String name);

    long countByTeamId(UUID teamId);

    Page<Collection> findByTeamId(UUID teamId, Pageable pageable);

    List<Collection> findByTeamIdAndNameContainingIgnoreCase(UUID teamId, String name);
}
