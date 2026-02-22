package com.codeops.courier.repository;

import com.codeops.courier.entity.EnvironmentVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, UUID> {

    List<EnvironmentVariable> findByEnvironmentId(UUID environmentId);

    List<EnvironmentVariable> findByCollectionId(UUID collectionId);

    List<EnvironmentVariable> findByEnvironmentIdAndIsEnabledTrue(UUID environmentId);

    List<EnvironmentVariable> findByCollectionIdAndIsEnabledTrue(UUID collectionId);

    void deleteByEnvironmentId(UUID environmentId);
}
