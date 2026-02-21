package com.codeops.courier.repository;

import com.codeops.courier.entity.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<Request, UUID> {

    List<Request> findByFolderIdOrderBySortOrder(UUID folderId);

    long countByFolderId(UUID folderId);
}
