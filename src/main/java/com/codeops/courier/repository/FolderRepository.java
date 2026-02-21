package com.codeops.courier.repository;

import com.codeops.courier.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findByCollectionIdOrderBySortOrder(UUID collectionId);

    List<Folder> findByParentFolderIdOrderBySortOrder(UUID parentFolderId);

    List<Folder> findByCollectionIdAndParentFolderIsNullOrderBySortOrder(UUID collectionId);

    long countByCollectionId(UUID collectionId);
}
