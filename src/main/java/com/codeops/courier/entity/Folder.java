package com.codeops.courier.entity;

import com.codeops.courier.entity.enums.AuthType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "folders",
        indexes = {
                @Index(name = "idx_folders_collection_id", columnList = "collection_id"),
                @Index(name = "idx_folders_parent_folder_id", columnList = "parent_folder_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Folder extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "pre_request_script", columnDefinition = "TEXT")
    private String preRequestScript;

    @Column(name = "post_response_script", columnDefinition = "TEXT")
    private String postResponseScript;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type")
    private AuthType authType;

    @Column(name = "auth_config", columnDefinition = "TEXT")
    private String authConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private Folder parentFolder;

    @Builder.Default
    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> subFolders = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Request> requests = new ArrayList<>();
}
