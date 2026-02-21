package com.codeops.courier.repository;

import com.codeops.courier.entity.RequestScript;
import com.codeops.courier.entity.enums.ScriptType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestScriptRepository extends JpaRepository<RequestScript, UUID> {

    List<RequestScript> findByRequestId(UUID requestId);

    Optional<RequestScript> findByRequestIdAndScriptType(UUID requestId, ScriptType scriptType);

    void deleteByRequestId(UUID requestId);
}
