package com.codeops.courier.repository;

import com.codeops.courier.entity.GlobalVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlobalVariableRepository extends JpaRepository<GlobalVariable, UUID> {

    List<GlobalVariable> findByTeamId(UUID teamId);

    List<GlobalVariable> findByTeamIdAndIsEnabledTrue(UUID teamId);

    Optional<GlobalVariable> findByTeamIdAndVariableKey(UUID teamId, String variableKey);

    boolean existsByTeamIdAndVariableKey(UUID teamId, String variableKey);
}
