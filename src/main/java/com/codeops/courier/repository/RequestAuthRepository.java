package com.codeops.courier.repository;

import com.codeops.courier.entity.RequestAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestAuthRepository extends JpaRepository<RequestAuth, UUID> {

    Optional<RequestAuth> findByRequestId(UUID requestId);

    void deleteByRequestId(UUID requestId);
}
