package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.CodeLanguage;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "code_snippet_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSnippetTemplate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private CodeLanguage language;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "template_content", columnDefinition = "TEXT", nullable = false)
    private String templateContent;

    @Column(name = "file_extension", nullable = false, length = 20)
    private String fileExtension;

    @Column(name = "content_type", length = 100)
    private String contentType;
}
