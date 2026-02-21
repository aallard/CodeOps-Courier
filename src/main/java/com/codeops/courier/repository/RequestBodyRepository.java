package com.codeops.courier.repository;

import com.codeops.courier.entity.RequestBody;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestBodyRepository extends JpaRepository<RequestBody, UUID> {

    Optional<RequestBody> findByRequestId(UUID requestId);

    void deleteByRequestId(UUID requestId);
}
