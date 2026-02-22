package com.codeops.courier.service;

import com.codeops.courier.dto.request.ShareCollectionRequest;
import com.codeops.courier.dto.request.UpdateSharePermissionRequest;
import com.codeops.courier.dto.response.CollectionShareResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.CollectionShare;
import com.codeops.courier.entity.enums.SharePermission;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import com.codeops.courier.repository.CollectionShareRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private CollectionShareRepository shareRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @InjectMocks
    private ShareService service;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID COLLECTION_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    // ─── shareCollection Tests ───

    @Test
    void shareCollection_success() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        when(shareRepository.existsByCollectionIdAndSharedWithUserId(COLLECTION_ID, USER_A)).thenReturn(false);
        when(shareRepository.save(any(CollectionShare.class))).thenAnswer(invocation -> {
            CollectionShare share = invocation.getArgument(0);
            share.setId(UUID.randomUUID());
            return share;
        });

        ShareCollectionRequest request = new ShareCollectionRequest(USER_A, SharePermission.EDITOR);
        CollectionShareResponse result = service.shareCollection(COLLECTION_ID, TEAM_ID, OWNER_ID, request);

        assertThat(result.collectionId()).isEqualTo(COLLECTION_ID);
        assertThat(result.sharedWithUserId()).isEqualTo(USER_A);
        assertThat(result.sharedByUserId()).isEqualTo(OWNER_ID);
        assertThat(result.permission()).isEqualTo(SharePermission.EDITOR);
        verify(shareRepository).save(any(CollectionShare.class));
    }

    @Test
    void shareCollection_alreadyShared_throws() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        when(shareRepository.existsByCollectionIdAndSharedWithUserId(COLLECTION_ID, USER_A)).thenReturn(true);

        ShareCollectionRequest request = new ShareCollectionRequest(USER_A, SharePermission.VIEWER);

        assertThatThrownBy(() -> service.shareCollection(COLLECTION_ID, TEAM_ID, OWNER_ID, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already shared");
    }

    @Test
    void shareCollection_sharingWithSelf_throws() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        ShareCollectionRequest request = new ShareCollectionRequest(OWNER_ID, SharePermission.VIEWER);

        assertThatThrownBy(() -> service.shareCollection(COLLECTION_ID, TEAM_ID, OWNER_ID, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void shareCollection_collectionNotFound_throws() {
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.empty());

        ShareCollectionRequest request = new ShareCollectionRequest(USER_A, SharePermission.VIEWER);

        assertThatThrownBy(() -> service.shareCollection(COLLECTION_ID, TEAM_ID, OWNER_ID, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Collection not found");
    }

    @Test
    void shareCollection_wrongTeam_throws() {
        Collection collection = buildCollection();
        collection.setTeamId(UUID.randomUUID());
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        ShareCollectionRequest request = new ShareCollectionRequest(USER_A, SharePermission.VIEWER);

        assertThatThrownBy(() -> service.shareCollection(COLLECTION_ID, TEAM_ID, OWNER_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── getCollectionShares / getSharedWithUser Tests ───

    @Test
    void getCollectionShares_success() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        CollectionShare share = buildShare(USER_A, SharePermission.EDITOR);
        when(shareRepository.findByCollectionId(COLLECTION_ID)).thenReturn(List.of(share));

        List<CollectionShareResponse> result = service.getCollectionShares(COLLECTION_ID, TEAM_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sharedWithUserId()).isEqualTo(USER_A);
        assertThat(result.get(0).permission()).isEqualTo(SharePermission.EDITOR);
    }

    @Test
    void getSharedWithUser_success() {
        CollectionShare share = buildShare(USER_A, SharePermission.VIEWER);
        when(shareRepository.findBySharedWithUserId(USER_A)).thenReturn(List.of(share));

        List<CollectionShareResponse> result = service.getSharedWithUser(USER_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sharedWithUserId()).isEqualTo(USER_A);
    }

    // ─── updateSharePermission Tests ───

    @Test
    void updateSharePermission_success() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        CollectionShare share = buildShare(USER_A, SharePermission.VIEWER);
        when(shareRepository.findByCollectionIdAndSharedWithUserId(COLLECTION_ID, USER_A))
                .thenReturn(Optional.of(share));
        when(shareRepository.save(any(CollectionShare.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateSharePermissionRequest request = new UpdateSharePermissionRequest(SharePermission.ADMIN);
        CollectionShareResponse result = service.updateSharePermission(COLLECTION_ID, USER_A, TEAM_ID, request);

        assertThat(result.permission()).isEqualTo(SharePermission.ADMIN);
    }

    @Test
    void updateSharePermission_notFound_throws() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        when(shareRepository.findByCollectionIdAndSharedWithUserId(COLLECTION_ID, USER_A))
                .thenReturn(Optional.empty());

        UpdateSharePermissionRequest request = new UpdateSharePermissionRequest(SharePermission.ADMIN);

        assertThatThrownBy(() -> service.updateSharePermission(COLLECTION_ID, USER_A, TEAM_ID, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Share not found");
    }

    // ─── revokeShare Tests ───

    @Test
    void revokeShare_success() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        CollectionShare share = buildShare(USER_A, SharePermission.EDITOR);
        when(shareRepository.findByCollectionIdAndSharedWithUserId(COLLECTION_ID, USER_A))
                .thenReturn(Optional.of(share));

        service.revokeShare(COLLECTION_ID, USER_A, TEAM_ID);

        verify(shareRepository).delete(share);
    }

    @Test
    void revokeShare_notFound_throws() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));
        when(shareRepository.findByCollectionIdAndSharedWithUserId(COLLECTION_ID, USER_A))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeShare(COLLECTION_ID, USER_A, TEAM_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Share not found");
    }

    // ─── Permission Tests ───

    @Test
    void hasPermission_ownerAlwaysTrue() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        assertThat(service.hasPermission(COLLECTION_ID, OWNER_ID, SharePermission.ADMIN)).isTrue();
        assertThat(service.hasPermission(COLLECTION_ID, OWNER_ID, SharePermission.EDITOR)).isTrue();
        assertThat(service.hasPermission(COLLECTION_ID, OWNER_ID, SharePermission.VIEWER)).isTrue();
    }

    @Test
    void hasPermission_viewerCanView() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        CollectionShare share = buildShare(USER_A, SharePermission.VIEWER);
        when(shareRepository.findByCollectionIdAndSharedWithUserId(COLLECTION_ID, USER_A))
                .thenReturn(Optional.of(share));

        assertThat(service.hasPermission(COLLECTION_ID, USER_A, SharePermission.VIEWER)).isTrue();
    }

    @Test
    void hasPermission_viewerCannotEdit() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        CollectionShare share = buildShare(USER_A, SharePermission.VIEWER);
        when(shareRepository.findByCollectionIdAndSharedWithUserId(COLLECTION_ID, USER_A))
                .thenReturn(Optional.of(share));

        assertThat(service.hasPermission(COLLECTION_ID, USER_A, SharePermission.EDITOR)).isFalse();
        assertThat(service.hasPermission(COLLECTION_ID, USER_A, SharePermission.ADMIN)).isFalse();
    }

    @Test
    void hasPermission_editorCanEdit() {
        Collection collection = buildCollection();
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(collection));

        CollectionShare share = buildShare(USER_A, SharePermission.EDITOR);
        when(shareRepository.findByCollectionIdAndSharedWithUserId(COLLECTION_ID, USER_A))
                .thenReturn(Optional.of(share));

        assertThat(service.hasPermission(COLLECTION_ID, USER_A, SharePermission.VIEWER)).isTrue();
        assertThat(service.hasPermission(COLLECTION_ID, USER_A, SharePermission.EDITOR)).isTrue();
        assertThat(service.hasPermission(COLLECTION_ID, USER_A, SharePermission.ADMIN)).isFalse();
    }

    // ─── Helper Methods ───

    private Collection buildCollection() {
        Collection collection = new Collection();
        collection.setId(COLLECTION_ID);
        collection.setTeamId(TEAM_ID);
        collection.setName("Test Collection");
        collection.setCreatedBy(OWNER_ID);
        return collection;
    }

    private CollectionShare buildShare(UUID sharedWithUserId, SharePermission permission) {
        CollectionShare share = new CollectionShare();
        share.setId(UUID.randomUUID());
        share.setCollection(buildCollection());
        share.setSharedWithUserId(sharedWithUserId);
        share.setSharedByUserId(OWNER_ID);
        share.setPermission(permission);
        return share;
    }
}
