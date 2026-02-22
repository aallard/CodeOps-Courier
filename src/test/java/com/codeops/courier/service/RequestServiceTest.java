package com.codeops.courier.service;

import com.codeops.courier.dto.mapper.RequestAuthMapper;
import com.codeops.courier.dto.mapper.RequestBodyMapper;
import com.codeops.courier.dto.mapper.RequestHeaderMapper;
import com.codeops.courier.dto.mapper.RequestMapper;
import com.codeops.courier.dto.mapper.RequestParamMapper;
import com.codeops.courier.dto.mapper.RequestScriptMapper;
import com.codeops.courier.dto.request.CreateRequestRequest;
import com.codeops.courier.dto.request.DuplicateRequestRequest;
import com.codeops.courier.dto.request.ReorderRequestRequest;
import com.codeops.courier.dto.request.SaveRequestAuthRequest;
import com.codeops.courier.dto.request.SaveRequestBodyRequest;
import com.codeops.courier.dto.request.SaveRequestHeadersRequest;
import com.codeops.courier.dto.request.SaveRequestParamsRequest;
import com.codeops.courier.dto.request.SaveRequestScriptRequest;
import com.codeops.courier.dto.request.UpdateRequestRequest;
import com.codeops.courier.dto.response.RequestAuthResponse;
import com.codeops.courier.dto.response.RequestBodyResponse;
import com.codeops.courier.dto.response.RequestHeaderResponse;
import com.codeops.courier.dto.response.RequestParamResponse;
import com.codeops.courier.dto.response.RequestScriptResponse;
import com.codeops.courier.dto.response.RequestSummaryResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestAuth;
import com.codeops.courier.entity.RequestBody;
import com.codeops.courier.entity.RequestHeader;
import com.codeops.courier.entity.RequestParam;
import com.codeops.courier.entity.RequestScript;
import com.codeops.courier.entity.enums.AuthType;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.entity.enums.ScriptType;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import com.codeops.courier.repository.FolderRepository;
import com.codeops.courier.repository.RequestAuthRepository;
import com.codeops.courier.repository.RequestBodyRepository;
import com.codeops.courier.repository.RequestHeaderRepository;
import com.codeops.courier.repository.RequestParamRepository;
import com.codeops.courier.repository.RequestRepository;
import com.codeops.courier.repository.RequestScriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RequestService covering all CRUD operations, duplication,
 * component management, reordering, and move operations.
 */
@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private RequestHeaderRepository requestHeaderRepository;

    @Mock
    private RequestParamRepository requestParamRepository;

    @Mock
    private RequestBodyRepository requestBodyRepository;

    @Mock
    private RequestAuthRepository requestAuthRepository;

    @Mock
    private RequestScriptRepository requestScriptRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private RequestMapper requestMapper;

    @Mock
    private RequestHeaderMapper requestHeaderMapper;

    @Mock
    private RequestParamMapper requestParamMapper;

    @Mock
    private RequestBodyMapper requestBodyMapper;

    @Mock
    private RequestAuthMapper requestAuthMapper;

    @Mock
    private RequestScriptMapper requestScriptMapper;

    @InjectMocks
    private RequestService requestService;

    private UUID teamId;
    private UUID collectionId;
    private UUID folderId;
    private UUID requestId;
    private Collection collection;
    private Folder folder;
    private Request request;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        collectionId = UUID.randomUUID();
        folderId = UUID.randomUUID();
        requestId = UUID.randomUUID();

        collection = new Collection();
        collection.setId(collectionId);
        collection.setTeamId(teamId);
        collection.setName("Test Collection");
        collection.setCreatedBy(UUID.randomUUID());
        collection.setCreatedAt(Instant.now());
        collection.setUpdatedAt(Instant.now());

        folder = new Folder();
        folder.setId(folderId);
        folder.setName("Test Folder");
        folder.setCollection(collection);
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());

        request = new Request();
        request.setId(requestId);
        request.setName("Test Request");
        request.setDescription("Test desc");
        request.setMethod(HttpMethod.GET);
        request.setUrl("https://api.test.com/users");
        request.setSortOrder(0);
        request.setFolder(folder);
        request.setHeaders(new ArrayList<>());
        request.setParams(new ArrayList<>());
        request.setScripts(new ArrayList<>());
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());
    }

    @Test
    void createRequest_success() {
        CreateRequestRequest createReq = new CreateRequestRequest(
                folderId, "New Request", "desc", HttpMethod.POST, "https://api.test.com/data", null);
        Request mapped = new Request();
        mapped.setName("New Request");
        mapped.setMethod(HttpMethod.POST);
        mapped.setUrl("https://api.test.com/data");

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(requestMapper.toEntity(createReq)).thenReturn(mapped);
        when(requestRepository.findByFolderIdOrderBySortOrder(folderId)).thenReturn(List.of());
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> {
            Request r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });
        mockEmptyComponents();

        var response = requestService.createRequest(teamId, createReq);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("New Request");
        verify(requestRepository).save(any(Request.class));
    }

    @Test
    void createRequest_folderNotFound_throws() {
        CreateRequestRequest createReq = new CreateRequestRequest(
                folderId, "Request", null, HttpMethod.GET, "https://url.com", null);
        when(folderRepository.findById(folderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.createRequest(teamId, createReq))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(folderId.toString());

        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequest_autoSortOrder() {
        Request existing = new Request();
        existing.setSortOrder(3);

        CreateRequestRequest createReq = new CreateRequestRequest(
                folderId, "Request", null, HttpMethod.GET, "https://url.com", null);
        Request mapped = new Request();
        mapped.setName("Request");
        mapped.setMethod(HttpMethod.GET);
        mapped.setUrl("https://url.com");

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(requestMapper.toEntity(createReq)).thenReturn(mapped);
        when(requestRepository.findByFolderIdOrderBySortOrder(folderId)).thenReturn(List.of(existing));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> {
            Request r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });
        mockEmptyComponents();

        var response = requestService.createRequest(teamId, createReq);

        assertThat(response.sortOrder()).isEqualTo(4);
    }

    @Test
    void getRequest_fullWithComponents() {
        RequestHeader header = new RequestHeader();
        header.setId(UUID.randomUUID());
        header.setHeaderKey("Content-Type");
        header.setHeaderValue("application/json");

        RequestHeaderResponse headerResp = new RequestHeaderResponse(
                header.getId(), "Content-Type", "application/json", null, true);

        RequestBody body = new RequestBody();
        body.setId(UUID.randomUUID());
        body.setBodyType(BodyType.RAW_JSON);
        body.setRawContent("{\"key\":\"value\"}");

        RequestBodyResponse bodyResp = new RequestBodyResponse(
                body.getId(), BodyType.RAW_JSON, "{\"key\":\"value\"}", null, null, null, null);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestHeaderRepository.findByRequestId(requestId)).thenReturn(List.of(header));
        when(requestHeaderMapper.toResponseList(List.of(header))).thenReturn(List.of(headerResp));
        when(requestParamRepository.findByRequestId(requestId)).thenReturn(List.of());
        when(requestParamMapper.toResponseList(List.of())).thenReturn(List.of());
        when(requestBodyRepository.findByRequestId(requestId)).thenReturn(Optional.of(body));
        when(requestBodyMapper.toResponse(body)).thenReturn(bodyResp);
        when(requestAuthRepository.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(requestScriptRepository.findByRequestId(requestId)).thenReturn(List.of());
        when(requestScriptMapper.toResponseList(List.of())).thenReturn(List.of());

        var response = requestService.getRequest(requestId, teamId);

        assertThat(response.headers()).hasSize(1);
        assertThat(response.headers().get(0).headerKey()).isEqualTo("Content-Type");
        assertThat(response.body()).isNotNull();
        assertThat(response.body().bodyType()).isEqualTo(BodyType.RAW_JSON);
        assertThat(response.auth()).isNull();
    }

    @Test
    void getRequest_notFound_throws() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.getRequest(requestId, teamId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(requestId.toString());
    }

    @Test
    void getRequest_wrongTeam_throws() {
        UUID wrongTeam = UUID.randomUUID();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> requestService.getRequest(requestId, wrongTeam))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getRequestsInFolder_success() {
        Request req1 = createTestRequest(UUID.randomUUID(), "Request 1");
        Request req2 = createTestRequest(UUID.randomUUID(), "Request 2");

        RequestSummaryResponse sum1 = new RequestSummaryResponse(
                req1.getId(), "Request 1", HttpMethod.GET, "https://url.com", 0);
        RequestSummaryResponse sum2 = new RequestSummaryResponse(
                req2.getId(), "Request 2", HttpMethod.GET, "https://url.com", 1);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(requestRepository.findByFolderIdOrderBySortOrder(folderId)).thenReturn(List.of(req1, req2));
        when(requestMapper.toSummaryResponse(req1)).thenReturn(sum1);
        when(requestMapper.toSummaryResponse(req2)).thenReturn(sum2);

        var result = requestService.getRequestsInFolder(folderId, teamId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Request 1");
    }

    @Test
    void updateRequest_partialUpdate() {
        UpdateRequestRequest updateReq = new UpdateRequestRequest(
                "Updated Name", null, null, null, null);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(Request.class))).thenReturn(request);
        mockEmptyComponents();

        var response = requestService.updateRequest(requestId, teamId, updateReq);

        assertThat(response).isNotNull();
        assertThat(request.getName()).isEqualTo("Updated Name");
        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(request.getUrl()).isEqualTo("https://api.test.com/users");
    }

    @Test
    void deleteRequest_success() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        requestService.deleteRequest(requestId, teamId);

        verify(requestRepository).delete(request);
    }

    @Test
    void duplicateRequest_sameFolder() {
        DuplicateRequestRequest dupReq = new DuplicateRequestRequest(null);

        mockEmptyComponentsForAnyId();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.findByFolderIdOrderBySortOrder(folderId)).thenReturn(List.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> {
            Request r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });

        var response = requestService.duplicateRequest(requestId, teamId, dupReq);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test Request (Copy)");
    }

    @Test
    void duplicateRequest_differentFolder() {
        UUID targetFolderId = UUID.randomUUID();
        Folder targetFolder = new Folder();
        targetFolder.setId(targetFolderId);
        targetFolder.setCollection(collection);
        targetFolder.setCreatedAt(Instant.now());
        targetFolder.setUpdatedAt(Instant.now());

        DuplicateRequestRequest dupReq = new DuplicateRequestRequest(targetFolderId);

        mockEmptyComponentsForAnyId();
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(folderRepository.findById(targetFolderId)).thenReturn(Optional.of(targetFolder));
        when(requestRepository.findByFolderIdOrderBySortOrder(targetFolderId)).thenReturn(List.of());
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> {
            Request r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });

        var response = requestService.duplicateRequest(requestId, teamId, dupReq);

        assertThat(response).isNotNull();

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue().getFolder()).isEqualTo(targetFolder);
    }

    @Test
    void duplicateRequest_deepCopiesComponents() {
        RequestHeader header = new RequestHeader();
        header.setHeaderKey("Authorization");
        header.setHeaderValue("Bearer token");
        header.setEnabled(true);

        RequestParam param = new RequestParam();
        param.setParamKey("page");
        param.setParamValue("1");
        param.setEnabled(true);

        RequestBody body = new RequestBody();
        body.setBodyType(BodyType.RAW_JSON);
        body.setRawContent("{\"data\":true}");

        RequestAuth auth = new RequestAuth();
        auth.setAuthType(AuthType.BEARER_TOKEN);
        auth.setBearerToken("test-token");

        RequestScript script = new RequestScript();
        script.setScriptType(ScriptType.PRE_REQUEST);
        script.setContent("console.log('test')");

        DuplicateRequestRequest dupReq = new DuplicateRequestRequest(null);

        // Generic empty stubs FIRST — handles buildFullResponse for the copy's new UUID
        mockEmptyComponentsForAnyId();
        // Specific stubs AFTER — override generic stubs for the source request's UUID (last-stub-wins)
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestHeaderRepository.findByRequestId(requestId)).thenReturn(List.of(header));
        when(requestParamRepository.findByRequestId(requestId)).thenReturn(List.of(param));
        when(requestBodyRepository.findByRequestId(requestId)).thenReturn(Optional.of(body));
        when(requestAuthRepository.findByRequestId(requestId)).thenReturn(Optional.of(auth));
        when(requestScriptRepository.findByRequestId(requestId)).thenReturn(List.of(script));
        when(requestRepository.findByFolderIdOrderBySortOrder(folderId)).thenReturn(List.of(request));
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> {
            Request r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });

        requestService.duplicateRequest(requestId, teamId, dupReq);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(captor.capture());
        Request copy = captor.getValue();

        assertThat(copy.getHeaders()).hasSize(1);
        assertThat(copy.getHeaders().get(0).getHeaderKey()).isEqualTo("Authorization");
        assertThat(copy.getParams()).hasSize(1);
        assertThat(copy.getParams().get(0).getParamKey()).isEqualTo("page");
        assertThat(copy.getBody()).isNotNull();
        assertThat(copy.getBody().getBodyType()).isEqualTo(BodyType.RAW_JSON);
        assertThat(copy.getAuth()).isNotNull();
        assertThat(copy.getAuth().getAuthType()).isEqualTo(AuthType.BEARER_TOKEN);
        assertThat(copy.getScripts()).hasSize(1);
        assertThat(copy.getScripts().get(0).getScriptType()).isEqualTo(ScriptType.PRE_REQUEST);
    }

    @Test
    void moveRequest_success() {
        UUID targetFolderId = UUID.randomUUID();
        Folder targetFolder = new Folder();
        targetFolder.setId(targetFolderId);
        targetFolder.setCollection(collection);
        targetFolder.setCreatedAt(Instant.now());
        targetFolder.setUpdatedAt(Instant.now());

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(folderRepository.findById(targetFolderId)).thenReturn(Optional.of(targetFolder));
        when(requestRepository.findByFolderIdOrderBySortOrder(targetFolderId)).thenReturn(List.of());
        when(requestRepository.save(any(Request.class))).thenAnswer(inv -> inv.getArgument(0));
        mockEmptyComponents();

        var response = requestService.moveRequest(requestId, teamId, targetFolderId);

        assertThat(response).isNotNull();
        assertThat(request.getFolder()).isEqualTo(targetFolder);
    }

    @Test
    void moveRequest_crossCollection_throws() {
        UUID targetFolderId = UUID.randomUUID();
        Collection otherCollection = new Collection();
        otherCollection.setId(UUID.randomUUID());
        otherCollection.setTeamId(teamId);

        Folder targetFolder = new Folder();
        targetFolder.setId(targetFolderId);
        targetFolder.setCollection(otherCollection);
        targetFolder.setCreatedAt(Instant.now());
        targetFolder.setUpdatedAt(Instant.now());

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(folderRepository.findById(targetFolderId)).thenReturn(Optional.of(targetFolder));

        assertThatThrownBy(() -> requestService.moveRequest(requestId, teamId, targetFolderId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("different collection");
    }

    @Test
    void reorderRequests_success() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Request req1 = createTestRequest(id1, "Request 1");
        req1.setSortOrder(1);
        Request req2 = createTestRequest(id2, "Request 2");
        req2.setSortOrder(0);

        RequestSummaryResponse sum1 = new RequestSummaryResponse(id1, "Request 1", HttpMethod.GET, "https://url.com", 0);
        RequestSummaryResponse sum2 = new RequestSummaryResponse(id2, "Request 2", HttpMethod.GET, "https://url.com", 1);

        ReorderRequestRequest reorderReq = new ReorderRequestRequest(List.of(id1, id2));

        when(requestRepository.findById(id1)).thenReturn(Optional.of(req1));
        when(requestRepository.findById(id2)).thenReturn(Optional.of(req2));
        when(requestRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(requestMapper.toSummaryResponse(req1)).thenReturn(sum1);
        when(requestMapper.toSummaryResponse(req2)).thenReturn(sum2);

        var result = requestService.reorderRequests(teamId, reorderReq);

        assertThat(result).hasSize(2);
        assertThat(req1.getSortOrder()).isZero();
        assertThat(req2.getSortOrder()).isEqualTo(1);
    }

    @Test
    void saveHeaders_replacesAll() {
        SaveRequestHeadersRequest saveReq = new SaveRequestHeadersRequest(List.of(
                new SaveRequestHeadersRequest.RequestHeaderEntry("Content-Type", "application/json", null, true),
                new SaveRequestHeadersRequest.RequestHeaderEntry("Accept", "text/html", null, true)
        ));

        RequestHeaderResponse resp1 = new RequestHeaderResponse(UUID.randomUUID(), "Content-Type", "application/json", null, true);
        RequestHeaderResponse resp2 = new RequestHeaderResponse(UUID.randomUUID(), "Accept", "text/html", null, true);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestHeaderRepository.saveAll(any())).thenAnswer(inv -> {
            List<RequestHeader> headers = inv.getArgument(0);
            headers.forEach(h -> h.setId(UUID.randomUUID()));
            return headers;
        });
        when(requestHeaderMapper.toResponseList(any())).thenReturn(List.of(resp1, resp2));

        var result = requestService.saveHeaders(requestId, teamId, saveReq);

        assertThat(result).hasSize(2);
        verify(requestHeaderRepository).deleteByRequestId(requestId);
        verify(requestHeaderRepository).flush();
        verify(requestHeaderRepository).saveAll(any());
    }

    @Test
    void saveHeaders_emptyList_clearsAll() {
        SaveRequestHeadersRequest saveReq = new SaveRequestHeadersRequest(List.of());

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestHeaderRepository.saveAll(any())).thenReturn(List.of());
        when(requestHeaderMapper.toResponseList(any())).thenReturn(List.of());

        var result = requestService.saveHeaders(requestId, teamId, saveReq);

        assertThat(result).isEmpty();
        verify(requestHeaderRepository).deleteByRequestId(requestId);
    }

    @Test
    void saveParams_replacesAll() {
        SaveRequestParamsRequest saveReq = new SaveRequestParamsRequest(List.of(
                new SaveRequestParamsRequest.RequestParamEntry("page", "1", null, true),
                new SaveRequestParamsRequest.RequestParamEntry("size", "20", null, true)
        ));

        RequestParamResponse resp1 = new RequestParamResponse(UUID.randomUUID(), "page", "1", null, true);
        RequestParamResponse resp2 = new RequestParamResponse(UUID.randomUUID(), "size", "20", null, true);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestParamRepository.saveAll(any())).thenAnswer(inv -> {
            List<RequestParam> params = inv.getArgument(0);
            params.forEach(p -> p.setId(UUID.randomUUID()));
            return params;
        });
        when(requestParamMapper.toResponseList(any())).thenReturn(List.of(resp1, resp2));

        var result = requestService.saveParams(requestId, teamId, saveReq);

        assertThat(result).hasSize(2);
        verify(requestParamRepository).deleteByRequestId(requestId);
        verify(requestParamRepository).flush();
    }

    @Test
    void saveBody_replacesExisting() {
        SaveRequestBodyRequest saveReq = new SaveRequestBodyRequest(
                BodyType.RAW_JSON, "{\"new\":true}", null, null, null, null);

        RequestBodyResponse bodyResp = new RequestBodyResponse(
                UUID.randomUUID(), BodyType.RAW_JSON, "{\"new\":true}", null, null, null, null);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestBodyRepository.save(any(RequestBody.class))).thenAnswer(inv -> {
            RequestBody b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        when(requestBodyMapper.toResponse(any(RequestBody.class))).thenReturn(bodyResp);

        var result = requestService.saveBody(requestId, teamId, saveReq);

        assertThat(result.bodyType()).isEqualTo(BodyType.RAW_JSON);
        verify(requestBodyRepository).deleteByRequestId(requestId);
        verify(requestBodyRepository).flush();
        verify(requestBodyRepository).save(any(RequestBody.class));
    }

    @Test
    void saveBody_createsNew() {
        SaveRequestBodyRequest saveReq = new SaveRequestBodyRequest(
                BodyType.FORM_DATA, null, "key=value", null, null, null);

        RequestBodyResponse bodyResp = new RequestBodyResponse(
                UUID.randomUUID(), BodyType.FORM_DATA, null, "key=value", null, null, null);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestBodyRepository.save(any(RequestBody.class))).thenAnswer(inv -> {
            RequestBody b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        when(requestBodyMapper.toResponse(any(RequestBody.class))).thenReturn(bodyResp);

        var result = requestService.saveBody(requestId, teamId, saveReq);

        assertThat(result.bodyType()).isEqualTo(BodyType.FORM_DATA);
        assertThat(result.formData()).isEqualTo("key=value");
    }

    @Test
    void saveAuth_replacesExisting() {
        SaveRequestAuthRequest saveReq = new SaveRequestAuthRequest(
                AuthType.BEARER_TOKEN, null, null, null, "new-token",
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        RequestAuthResponse authResp = new RequestAuthResponse(
                UUID.randomUUID(), AuthType.BEARER_TOKEN, null, null, null, "new-token",
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestAuthRepository.save(any(RequestAuth.class))).thenAnswer(inv -> {
            RequestAuth a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(requestAuthMapper.toResponse(any(RequestAuth.class))).thenReturn(authResp);

        var result = requestService.saveAuth(requestId, teamId, saveReq);

        assertThat(result.authType()).isEqualTo(AuthType.BEARER_TOKEN);
        verify(requestAuthRepository).deleteByRequestId(requestId);
        verify(requestAuthRepository).flush();
    }

    @Test
    void saveScript_preRequest_success() {
        SaveRequestScriptRequest saveReq = new SaveRequestScriptRequest(
                ScriptType.PRE_REQUEST, "console.log('pre')");

        RequestScriptResponse scriptResp = new RequestScriptResponse(
                UUID.randomUUID(), ScriptType.PRE_REQUEST, "console.log('pre')");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestScriptRepository.findByRequestIdAndScriptType(requestId, ScriptType.PRE_REQUEST))
                .thenReturn(Optional.empty());
        when(requestScriptRepository.save(any(RequestScript.class))).thenAnswer(inv -> {
            RequestScript s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(requestScriptMapper.toResponse(any(RequestScript.class))).thenReturn(scriptResp);

        var result = requestService.saveScript(requestId, teamId, saveReq);

        assertThat(result.scriptType()).isEqualTo(ScriptType.PRE_REQUEST);
        assertThat(result.content()).isEqualTo("console.log('pre')");
    }

    @Test
    void saveScript_postResponse_success() {
        SaveRequestScriptRequest saveReq = new SaveRequestScriptRequest(
                ScriptType.POST_RESPONSE, "console.log('post')");

        RequestScriptResponse scriptResp = new RequestScriptResponse(
                UUID.randomUUID(), ScriptType.POST_RESPONSE, "console.log('post')");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestScriptRepository.findByRequestIdAndScriptType(requestId, ScriptType.POST_RESPONSE))
                .thenReturn(Optional.empty());
        when(requestScriptRepository.save(any(RequestScript.class))).thenAnswer(inv -> {
            RequestScript s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(requestScriptMapper.toResponse(any(RequestScript.class))).thenReturn(scriptResp);

        var result = requestService.saveScript(requestId, teamId, saveReq);

        assertThat(result.scriptType()).isEqualTo(ScriptType.POST_RESPONSE);
    }

    @Test
    void saveScript_updatesExisting() {
        RequestScript existing = new RequestScript();
        existing.setId(UUID.randomUUID());
        existing.setScriptType(ScriptType.PRE_REQUEST);
        existing.setContent("old content");
        existing.setRequest(request);

        SaveRequestScriptRequest saveReq = new SaveRequestScriptRequest(
                ScriptType.PRE_REQUEST, "new content");

        RequestScriptResponse scriptResp = new RequestScriptResponse(
                existing.getId(), ScriptType.PRE_REQUEST, "new content");

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestScriptRepository.findByRequestIdAndScriptType(requestId, ScriptType.PRE_REQUEST))
                .thenReturn(Optional.of(existing));
        when(requestScriptRepository.save(any(RequestScript.class))).thenReturn(existing);
        when(requestScriptMapper.toResponse(any(RequestScript.class))).thenReturn(scriptResp);

        var result = requestService.saveScript(requestId, teamId, saveReq);

        assertThat(result.content()).isEqualTo("new content");
        assertThat(existing.getContent()).isEqualTo("new content");
    }

    @Test
    void clearRequestComponents_success() {
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        requestService.clearRequestComponents(requestId, teamId);

        verify(requestHeaderRepository).deleteByRequestId(requestId);
        verify(requestParamRepository).deleteByRequestId(requestId);
        verify(requestBodyRepository).deleteByRequestId(requestId);
        verify(requestAuthRepository).deleteByRequestId(requestId);
        verify(requestScriptRepository).deleteByRequestId(requestId);
    }

    private void mockEmptyComponents() {
        when(requestHeaderRepository.findByRequestId(any(UUID.class))).thenReturn(List.of());
        when(requestParamRepository.findByRequestId(any(UUID.class))).thenReturn(List.of());
        when(requestBodyRepository.findByRequestId(any(UUID.class))).thenReturn(Optional.empty());
        when(requestAuthRepository.findByRequestId(any(UUID.class))).thenReturn(Optional.empty());
        when(requestScriptRepository.findByRequestId(any(UUID.class))).thenReturn(List.of());
        when(requestHeaderMapper.toResponseList(any())).thenReturn(List.of());
        when(requestParamMapper.toResponseList(any())).thenReturn(List.of());
        when(requestScriptMapper.toResponseList(any())).thenReturn(List.of());
    }

    private void mockEmptyComponentsForAnyId() {
        when(requestHeaderRepository.findByRequestId(any(UUID.class))).thenReturn(List.of());
        when(requestParamRepository.findByRequestId(any(UUID.class))).thenReturn(List.of());
        when(requestBodyRepository.findByRequestId(any(UUID.class))).thenReturn(Optional.empty());
        when(requestAuthRepository.findByRequestId(any(UUID.class))).thenReturn(Optional.empty());
        when(requestScriptRepository.findByRequestId(any(UUID.class))).thenReturn(List.of());
        when(requestHeaderMapper.toResponseList(any())).thenReturn(List.of());
        when(requestParamMapper.toResponseList(any())).thenReturn(List.of());
        when(requestScriptMapper.toResponseList(any())).thenReturn(List.of());
    }

    private Request createTestRequest(UUID id, String name) {
        Request r = new Request();
        r.setId(id);
        r.setName(name);
        r.setMethod(HttpMethod.GET);
        r.setUrl("https://url.com");
        r.setSortOrder(0);
        r.setFolder(folder);
        r.setHeaders(new ArrayList<>());
        r.setParams(new ArrayList<>());
        r.setScripts(new ArrayList<>());
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return r;
    }
}
