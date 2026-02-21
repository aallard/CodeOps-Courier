package com.codeops.courier.repository;

import com.codeops.courier.entity.RequestHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestHeaderRepository extends JpaRepository<RequestHeader, UUID> {

    List<RequestHeader> findByRequestId(UUID requestId);

    void deleteByRequestId(UUID requestId);
}
