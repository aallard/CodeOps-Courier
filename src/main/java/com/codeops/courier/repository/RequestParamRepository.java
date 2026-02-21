package com.codeops.courier.repository;

import com.codeops.courier.entity.RequestParam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestParamRepository extends JpaRepository<RequestParam, UUID> {

    List<RequestParam> findByRequestId(UUID requestId);

    void deleteByRequestId(UUID requestId);
}
