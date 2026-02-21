package com.codeops.courier.repository;

import com.codeops.courier.entity.CodeSnippetTemplate;
import com.codeops.courier.entity.enums.CodeLanguage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CodeSnippetTemplateRepository extends JpaRepository<CodeSnippetTemplate, UUID> {

    Optional<CodeSnippetTemplate> findByLanguage(CodeLanguage language);

    List<CodeSnippetTemplate> findAllByOrderByDisplayNameAsc();

    boolean existsByLanguage(CodeLanguage language);
}
