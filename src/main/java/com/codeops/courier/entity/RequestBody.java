package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.BodyType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "request_bodies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestBody extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", nullable = false)
    private BodyType bodyType;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "form_data", columnDefinition = "TEXT")
    private String formData;

    @Column(name = "graphql_query", columnDefinition = "TEXT")
    private String graphqlQuery;

    @Column(name = "graphql_variables", columnDefinition = "TEXT")
    private String graphqlVariables;

    @Column(name = "binary_file_name", length = 500)
    private String binaryFileName;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private Request request;
}
