package com.codeops.courier.repository;

import com.codeops.courier.entity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    List<Environment> findByTeamId(UUID teamId);

    Optional<Environment> findByTeamIdAndName(UUID teamId, String name);

    Optional<Environment> findByTeamIdAndIsActiveTrue(UUID teamId);

    boolean existsByTeamIdAndName(UUID teamId, String name);

    long countByTeamId(UUID teamId);
}
