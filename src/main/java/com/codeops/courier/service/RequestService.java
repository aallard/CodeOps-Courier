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
import com.codeops.courier.dto.response.RequestResponse;
import com.codeops.courier.dto.response.RequestScriptResponse;
import com.codeops.courier.dto.response.RequestSummaryResponse;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestAuth;
import com.codeops.courier.entity.RequestBody;
import com.codeops.courier.entity.RequestHeader;
import com.codeops.courier.entity.RequestParam;
import com.codeops.courier.entity.RequestScript;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing API requests including CRUD operations, duplication,
 * reordering, and component management (headers, params, body, auth, scripts).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class RequestService {

    private final RequestRepository requestRepository;
    private final RequestHeaderRepository requestHeaderRepository;
    private final RequestParamRepository requestParamRepository;
    private final RequestBodyRepository requestBodyRepository;
    private final RequestAuthRepository requestAuthRepository;
    private final RequestScriptRepository requestScriptRepository;
    private final FolderRepository folderRepository;
    private final CollectionRepository collectionRepository;
    private final RequestMapper requestMapper;
    private final RequestHeaderMapper requestHeaderMapper;
    private final RequestParamMapper requestParamMapper;
    private final RequestBodyMapper requestBodyMapper;
    private final RequestAuthMapper requestAuthMapper;
    private final RequestScriptMapper requestScriptMapper;

    /**
     * Creates a new request in a folder.
     *
     * @param teamId  the team ID for access validation
     * @param request the request creation request
     * @return the created request response with empty components
     * @throws NotFoundException if the folder is not found or belongs to a different team
     */
    public RequestResponse createRequest(UUID teamId, CreateRequestRequest request) {
        Folder folder = findFolderAndValidateTeam(request.folderId(), teamId);

        Request entity = requestMapper.toEntity(request);
        entity.setFolder(folder);

        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        } else {
            entity.setSortOrder(computeNextRequestSortOrder(request.folderId()));
        }

        Request saved = requestRepository.save(entity);
        log.info("Created request '{}' in folder {}", saved.getName(), request.folderId());
        return buildFullResponse(saved);
    }

    /**
     * Gets a single request with all components (headers, params, body, auth, scripts).
     *
     * @param requestId the request ID
     * @param teamId    the team ID for access validation
     * @return the full request response with all components
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public RequestResponse getRequest(UUID requestId, UUID teamId) {
        Request request = findRequestAndValidateTeam(requestId, teamId);
        return buildFullResponse(request);
    }

    /**
     * Gets requests in a folder (summary only — no components loaded).
     *
     * @param folderId the folder ID
     * @param teamId   the team ID for access validation
     * @return the list of request summaries
     * @throws NotFoundException if the folder is not found or belongs to a different team
     */
    @Transactional(readOnly = true)
    public List<RequestSummaryResponse> getRequestsInFolder(UUID folderId, UUID teamId) {
        findFolderAndValidateTeam(folderId, teamId);
        List<Request> requests = requestRepository.findByFolderIdOrderBySortOrder(folderId);
        return requests.stream().map(requestMapper::toSummaryResponse).toList();
    }

    /**
     * Updates a request with partial update semantics — only non-null fields are applied.
     *
     * @param requestId the request ID
     * @param teamId    the team ID for access validation
     * @param request   the update request with optional fields
     * @return the updated request response with all components
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    public RequestResponse updateRequest(UUID requestId, UUID teamId, UpdateRequestRequest request) {
        Request entity = findRequestAndValidateTeam(requestId, teamId);

        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.method() != null) {
            entity.setMethod(request.method());
        }
        if (request.url() != null) {
            entity.setUrl(request.url());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }

        Request saved = requestRepository.save(entity);
        log.info("Updated request '{}'", saved.getName());
        return buildFullResponse(saved);
    }

    /**
     * Deletes a request and all its components via JPA cascade.
     *
     * @param requestId the request ID
     * @param teamId    the team ID for access validation
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    public void deleteRequest(UUID requestId, UUID teamId) {
        Request request = findRequestAndValidateTeam(requestId, teamId);
        requestRepository.delete(request);
        log.info("Deleted request '{}' ({})", request.getName(), requestId);
    }

    /**
     * Duplicates a request with deep copy of all components (headers, params, body, auth, scripts).
     *
     * @param requestId  the source request ID
     * @param teamId     the team ID for access validation
     * @param dupRequest the duplication request with optional target folder
     * @return the duplicated request response with all copied components
     * @throws NotFoundException   if the source request or target folder is not found
     * @throws ValidationException if the target folder belongs to a different collection
     */
    public RequestResponse duplicateRequest(UUID requestId, UUID teamId, DuplicateRequestRequest dupRequest) {
        Request source = findRequestAndValidateTeam(requestId, teamId);

        Folder targetFolder;
        if (dupRequest.targetFolderId() != null) {
            targetFolder = findFolderAndValidateTeam(dupRequest.targetFolderId(), teamId);
            if (!targetFolder.getCollection().getId().equals(source.getFolder().getCollection().getId())) {
                throw new ValidationException("Cannot duplicate request to a different collection");
            }
        } else {
            targetFolder = source.getFolder();
        }

        Request copy = new Request();
        copy.setName(source.getName() + " (Copy)");
        copy.setDescription(source.getDescription());
        copy.setMethod(source.getMethod());
        copy.setUrl(source.getUrl());
        copy.setFolder(targetFolder);
        copy.setSortOrder(computeNextRequestSortOrder(targetFolder.getId()));

        List<RequestHeader> sourceHeaders = requestHeaderRepository.findByRequestId(source.getId());
        for (RequestHeader h : sourceHeaders) {
            RequestHeader hCopy = new RequestHeader();
            hCopy.setHeaderKey(h.getHeaderKey());
            hCopy.setHeaderValue(h.getHeaderValue());
            hCopy.setDescription(h.getDescription());
            hCopy.setEnabled(h.isEnabled());
            hCopy.setRequest(copy);
            copy.getHeaders().add(hCopy);
        }

        List<RequestParam> sourceParams = requestParamRepository.findByRequestId(source.getId());
        for (RequestParam p : sourceParams) {
            RequestParam pCopy = new RequestParam();
            pCopy.setParamKey(p.getParamKey());
            pCopy.setParamValue(p.getParamValue());
            pCopy.setDescription(p.getDescription());
            pCopy.setEnabled(p.isEnabled());
            pCopy.setRequest(copy);
            copy.getParams().add(pCopy);
        }

        requestBodyRepository.findByRequestId(source.getId()).ifPresent(b -> {
            RequestBody bCopy = new RequestBody();
            bCopy.setBodyType(b.getBodyType());
            bCopy.setRawContent(b.getRawContent());
            bCopy.setFormData(b.getFormData());
            bCopy.setGraphqlQuery(b.getGraphqlQuery());
            bCopy.setGraphqlVariables(b.getGraphqlVariables());
            bCopy.setBinaryFileName(b.getBinaryFileName());
            bCopy.setRequest(copy);
            copy.setBody(bCopy);
        });

        requestAuthRepository.findByRequestId(source.getId()).ifPresent(a -> {
            RequestAuth aCopy = new RequestAuth();
            aCopy.setAuthType(a.getAuthType());
            aCopy.setApiKeyHeader(a.getApiKeyHeader());
            aCopy.setApiKeyValue(a.getApiKeyValue());
            aCopy.setApiKeyAddTo(a.getApiKeyAddTo());
            aCopy.setBearerToken(a.getBearerToken());
            aCopy.setBasicUsername(a.getBasicUsername());
            aCopy.setBasicPassword(a.getBasicPassword());
            aCopy.setOauth2GrantType(a.getOauth2GrantType());
            aCopy.setOauth2AuthUrl(a.getOauth2AuthUrl());
            aCopy.setOauth2TokenUrl(a.getOauth2TokenUrl());
            aCopy.setOauth2ClientId(a.getOauth2ClientId());
            aCopy.setOauth2ClientSecret(a.getOauth2ClientSecret());
            aCopy.setOauth2Scope(a.getOauth2Scope());
            aCopy.setOauth2CallbackUrl(a.getOauth2CallbackUrl());
            aCopy.setOauth2AccessToken(a.getOauth2AccessToken());
            aCopy.setJwtSecret(a.getJwtSecret());
            aCopy.setJwtPayload(a.getJwtPayload());
            aCopy.setJwtAlgorithm(a.getJwtAlgorithm());
            aCopy.setRequest(copy);
            copy.setAuth(aCopy);
        });

        List<RequestScript> sourceScripts = requestScriptRepository.findByRequestId(source.getId());
        for (RequestScript s : sourceScripts) {
            RequestScript sCopy = new RequestScript();
            sCopy.setScriptType(s.getScriptType());
            sCopy.setContent(s.getContent());
            sCopy.setRequest(copy);
            copy.getScripts().add(sCopy);
        }

        Request saved = requestRepository.save(copy);
        log.info("Duplicated request '{}' as '{}'", source.getName(), saved.getName());
        return buildFullResponse(saved);
    }

    /**
     * Moves a request to a different folder within the same collection.
     *
     * @param requestId      the request ID to move
     * @param teamId         the team ID for access validation
     * @param targetFolderId the target folder ID
     * @return the moved request response with all components
     * @throws NotFoundException   if the request or target folder is not found
     * @throws ValidationException if the target folder belongs to a different collection
     */
    public RequestResponse moveRequest(UUID requestId, UUID teamId, UUID targetFolderId) {
        Request request = findRequestAndValidateTeam(requestId, teamId);
        Folder targetFolder = findFolderAndValidateTeam(targetFolderId, teamId);

        if (!targetFolder.getCollection().getId().equals(request.getFolder().getCollection().getId())) {
            throw new ValidationException("Cannot move request to a different collection");
        }

        request.setFolder(targetFolder);
        request.setSortOrder(computeNextRequestSortOrder(targetFolderId));

        Request saved = requestRepository.save(request);
        log.info("Moved request '{}' to folder {}", saved.getName(), targetFolderId);
        return buildFullResponse(saved);
    }

    /**
     * Reorders requests within a folder. Accepts an ordered list of request IDs
     * representing the new sort order.
     *
     * @param teamId  the team ID for access validation
     * @param request the reorder request containing the ordered request IDs
     * @return the reordered request summaries
     * @throws NotFoundException   if any request ID is not found
     * @throws ValidationException if requests belong to different folders
     */
    public List<RequestSummaryResponse> reorderRequests(UUID teamId, ReorderRequestRequest request) {
        List<Request> requests = new ArrayList<>();
        UUID firstFolderId = null;

        for (int i = 0; i < request.requestIds().size(); i++) {
            UUID reqId = request.requestIds().get(i);
            Request req = findRequestAndValidateTeam(reqId, teamId);

            if (i == 0) {
                firstFolderId = req.getFolder().getId();
            } else if (!req.getFolder().getId().equals(firstFolderId)) {
                throw new ValidationException("All requests must belong to the same folder");
            }

            req.setSortOrder(i);
            requests.add(req);
        }

        List<Request> saved = requestRepository.saveAll(requests);
        log.info("Reordered {} requests", saved.size());
        return saved.stream().map(requestMapper::toSummaryResponse).toList();
    }

    /**
     * Saves headers for a request (replace all — batch save).
     * Deletes all existing headers and creates new ones from the request.
     *
     * @param requestId the request ID
     * @param teamId    the team ID for access validation
     * @param request   the save request containing header entries
     * @return the saved header responses
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    public List<RequestHeaderResponse> saveHeaders(UUID requestId, UUID teamId, SaveRequestHeadersRequest request) {
        Request req = findRequestAndValidateTeam(requestId, teamId);
        requestHeaderRepository.deleteByRequestId(requestId);
        requestHeaderRepository.flush();

        List<RequestHeader> headers = request.headers().stream().map(entry -> {
            RequestHeader h = new RequestHeader();
            h.setHeaderKey(entry.headerKey());
            h.setHeaderValue(entry.headerValue());
            h.setDescription(entry.description());
            h.setEnabled(entry.isEnabled());
            h.setRequest(req);
            return h;
        }).toList();

        List<RequestHeader> saved = requestHeaderRepository.saveAll(headers);
        log.info("Saved {} headers for request {}", saved.size(), requestId);
        return requestHeaderMapper.toResponseList(saved);
    }

    /**
     * Saves params for a request (replace all — batch save).
     * Deletes all existing params and creates new ones from the request.
     *
     * @param requestId the request ID
     * @param teamId    the team ID for access validation
     * @param request   the save request containing param entries
     * @return the saved param responses
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    public List<RequestParamResponse> saveParams(UUID requestId, UUID teamId, SaveRequestParamsRequest request) {
        Request req = findRequestAndValidateTeam(requestId, teamId);
        requestParamRepository.deleteByRequestId(requestId);
        requestParamRepository.flush();

        List<RequestParam> params = request.params().stream().map(entry -> {
            RequestParam p = new RequestParam();
            p.setParamKey(entry.paramKey());
            p.setParamValue(entry.paramValue());
            p.setDescription(entry.description());
            p.setEnabled(entry.isEnabled());
            p.setRequest(req);
            return p;
        }).toList();

        List<RequestParam> saved = requestParamRepository.saveAll(params);
        log.info("Saved {} params for request {}", saved.size(), requestId);
        return requestParamMapper.toResponseList(saved);
    }

    /**
     * Saves body for a request (replace — single record).
     * Deletes existing body if any and creates a new one.
     *
     * @param requestId the request ID
     * @param teamId    the team ID for access validation
     * @param request   the save request containing body data
     * @return the saved body response
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    public RequestBodyResponse saveBody(UUID requestId, UUID teamId, SaveRequestBodyRequest request) {
        Request req = findRequestAndValidateTeam(requestId, teamId);
        requestBodyRepository.deleteByRequestId(requestId);
        requestBodyRepository.flush();

        RequestBody body = new RequestBody();
        body.setBodyType(request.bodyType());
        body.setRawContent(request.rawContent());
        body.setFormData(request.formData());
        body.setGraphqlQuery(request.graphqlQuery());
        body.setGraphqlVariables(request.graphqlVariables());
        body.setBinaryFileName(request.binaryFileName());
        body.setRequest(req);

        RequestBody saved = requestBodyRepository.save(body);
        log.info("Saved body for request {}", requestId);
        return requestBodyMapper.toResponse(saved);
    }

    /**
     * Saves auth for a request (replace — single record).
     * Deletes existing auth if any and creates a new one.
     *
     * @param requestId the request ID
     * @param teamId    the team ID for access validation
     * @param request   the save request containing auth data
     * @return the saved auth response
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    public RequestAuthResponse saveAuth(UUID requestId, UUID teamId, SaveRequestAuthRequest request) {
        Request req = findRequestAndValidateTeam(requestId, teamId);
        requestAuthRepository.deleteByRequestId(requestId);
        requestAuthRepository.flush();

        RequestAuth auth = new RequestAuth();
        auth.setAuthType(request.authType());
        auth.setApiKeyHeader(request.apiKeyHeader());
        auth.setApiKeyValue(request.apiKeyValue());
        auth.setApiKeyAddTo(request.apiKeyAddTo());
        auth.setBearerToken(request.bearerToken());
        auth.setBasicUsername(request.basicUsername());
        auth.setBasicPassword(request.basicPassword());
        auth.setOauth2GrantType(request.oauth2GrantType());
        auth.setOauth2AuthUrl(request.oauth2AuthUrl());
        auth.setOauth2TokenUrl(request.oauth2TokenUrl());
        auth.setOauth2ClientId(request.oauth2ClientId());
        auth.setOauth2ClientSecret(request.oauth2ClientSecret());
        auth.setOauth2Scope(request.oauth2Scope());
        auth.setOauth2CallbackUrl(request.oauth2CallbackUrl());
        auth.setOauth2AccessToken(request.oauth2AccessToken());
        auth.setJwtSecret(request.jwtSecret());
        auth.setJwtPayload(request.jwtPayload());
        auth.setJwtAlgorithm(request.jwtAlgorithm());
        auth.setRequest(req);

        RequestAuth saved = requestAuthRepository.save(auth);
        log.info("Saved auth for request {}", requestId);
        return requestAuthMapper.toResponse(saved);
    }

    /**
     * Saves a script for a request (replace by script type).
     * Updates existing script if one exists for the type, otherwise creates a new one.
     *
     * @param requestId the request ID
     * @param teamId    the team ID for access validation
     * @param request   the save request containing script data
     * @return the saved script response
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    public RequestScriptResponse saveScript(UUID requestId, UUID teamId, SaveRequestScriptRequest request) {
        Request req = findRequestAndValidateTeam(requestId, teamId);

        RequestScript script = requestScriptRepository
                .findByRequestIdAndScriptType(requestId, request.scriptType())
                .orElseGet(() -> {
                    RequestScript s = new RequestScript();
                    s.setScriptType(request.scriptType());
                    s.setRequest(req);
                    return s;
                });

        script.setContent(request.content());

        RequestScript saved = requestScriptRepository.save(script);
        log.info("Saved {} script for request {}", request.scriptType(), requestId);
        return requestScriptMapper.toResponse(saved);
    }

    /**
     * Deletes all components of a request (headers, params, body, auth, scripts)
     * without deleting the request itself. Used for "clear request" functionality.
     *
     * @param requestId the request ID
     * @param teamId    the team ID for access validation
     * @throws NotFoundException if the request is not found or belongs to a different team
     */
    public void clearRequestComponents(UUID requestId, UUID teamId) {
        findRequestAndValidateTeam(requestId, teamId);
        requestHeaderRepository.deleteByRequestId(requestId);
        requestParamRepository.deleteByRequestId(requestId);
        requestBodyRepository.deleteByRequestId(requestId);
        requestAuthRepository.deleteByRequestId(requestId);
        requestScriptRepository.deleteByRequestId(requestId);
        log.info("Cleared all components for request {}", requestId);
    }

    /**
     * Finds a request by ID and validates team ownership through the folder/collection chain.
     *
     * @param requestId the request ID
     * @param teamId    the expected team ID
     * @return the request entity
     * @throws NotFoundException if not found or wrong team
     */
    private Request findRequestAndValidateTeam(UUID requestId, UUID teamId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found: " + requestId));
        if (!request.getFolder().getCollection().getTeamId().equals(teamId)) {
            throw new NotFoundException("Request not found: " + requestId);
        }
        return request;
    }

    /**
     * Finds a folder by ID and validates team ownership through the collection.
     *
     * @param folderId the folder ID
     * @param teamId   the expected team ID
     * @return the folder entity
     * @throws NotFoundException if not found or wrong team
     */
    private Folder findFolderAndValidateTeam(UUID folderId, UUID teamId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new NotFoundException("Folder not found: " + folderId));
        if (!folder.getCollection().getTeamId().equals(teamId)) {
            throw new NotFoundException("Folder not found: " + folderId);
        }
        return folder;
    }

    private RequestResponse buildFullResponse(Request request) {
        List<RequestHeaderResponse> headers = requestHeaderMapper.toResponseList(
                requestHeaderRepository.findByRequestId(request.getId()));
        List<RequestParamResponse> params = requestParamMapper.toResponseList(
                requestParamRepository.findByRequestId(request.getId()));
        RequestBodyResponse body = requestBodyRepository.findByRequestId(request.getId())
                .map(requestBodyMapper::toResponse)
                .orElse(null);
        RequestAuthResponse auth = requestAuthRepository.findByRequestId(request.getId())
                .map(requestAuthMapper::toResponse)
                .orElse(null);
        List<RequestScriptResponse> scripts = requestScriptMapper.toResponseList(
                requestScriptRepository.findByRequestId(request.getId()));

        return new RequestResponse(
                request.getId(),
                request.getFolder().getId(),
                request.getName(),
                request.getDescription(),
                request.getMethod(),
                request.getUrl(),
                request.getSortOrder(),
                headers,
                params,
                body,
                auth,
                scripts,
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }

    private int computeNextRequestSortOrder(UUID folderId) {
        List<Request> siblings = requestRepository.findByFolderIdOrderBySortOrder(folderId);
        return siblings.stream().mapToInt(Request::getSortOrder).max().orElse(-1) + 1;
    }
}
