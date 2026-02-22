package com.codeops.courier.service;

import com.codeops.courier.dto.mapper.RunResultMapper;
import com.codeops.courier.dto.request.StartCollectionRunRequest;
import com.codeops.courier.dto.response.PageResponse;
import com.codeops.courier.dto.response.ProxyResponse;
import com.codeops.courier.dto.response.RunIterationResponse;
import com.codeops.courier.dto.response.RunResultDetailResponse;
import com.codeops.courier.dto.response.RunResultResponse;
import com.codeops.courier.entity.Collection;
import com.codeops.courier.entity.Folder;
import com.codeops.courier.entity.Request;
import com.codeops.courier.entity.RequestBody;
import com.codeops.courier.entity.RequestHeader;
import com.codeops.courier.entity.RequestScript;
import com.codeops.courier.entity.RunIteration;
import com.codeops.courier.entity.RunResult;
import com.codeops.courier.entity.enums.BodyType;
import com.codeops.courier.entity.enums.HttpMethod;
import com.codeops.courier.entity.enums.RunStatus;
import com.codeops.courier.entity.enums.ScriptType;
import com.codeops.courier.exception.NotFoundException;
import com.codeops.courier.exception.ValidationException;
import com.codeops.courier.repository.CollectionRepository;
import com.codeops.courier.repository.FolderRepository;
import com.codeops.courier.repository.RequestRepository;
import com.codeops.courier.repository.RequestScriptRepository;
import com.codeops.courier.repository.RunIterationRepository;
import com.codeops.courier.repository.RunResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionRunnerServiceTest {

    @Mock private RunResultRepository runResultRepository;
    @Mock private RunIterationRepository runIterationRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private RequestScriptRepository requestScriptRepository;
    @Mock private RequestProxyService requestProxyService;
    @Mock private ScriptEngineService scriptEngineService;
    @Mock private VariableService variableService;
    @Mock private DataFileParser dataFileParser;
    @Mock private RunResultMapper runResultMapper;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CollectionRunnerService service;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COLLECTION_ID = UUID.randomUUID();
    private static final UUID ENV_ID = UUID.randomUUID();

    private Collection testCollection;
    private Folder rootFolder;
    private Request testRequest;

    @BeforeEach
    void setUp() {
        testCollection = buildCollection(COLLECTION_ID, TEAM_ID);
        rootFolder = buildFolder("Root", 0, testCollection, null);
        testRequest = buildRequest("GET Users", HttpMethod.GET, "https://api.example.com/users", 0, rootFolder);

        // Default mapper stubs
        lenient().when(runResultMapper.toResponse(any(RunResult.class)))
                .thenAnswer(inv -> {
                    RunResult r = inv.getArgument(0);
                    return new RunResultResponse(r.getId(), r.getTeamId(), r.getCollectionId(),
                            r.getEnvironmentId(), r.getStatus(), r.getTotalRequests(),
                            r.getPassedRequests(), r.getFailedRequests(), r.getTotalAssertions(),
                            r.getPassedAssertions(), r.getFailedAssertions(), r.getTotalDurationMs(),
                            r.getIterationCount(), r.getDelayBetweenRequestsMs(), r.getDataFilename(),
                            r.getStartedAt(), r.getCompletedAt(), r.getStartedByUserId(), r.getCreatedAt());
                });
        lenient().when(runResultMapper.toIterationResponseList(any()))
                .thenReturn(List.of());
    }

    // ─── startRun Tests ───

    @Test
    void startRun_emptyCollection_completesWithZeroRequests() {
        setupRunStart();
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of());

        RunResultDetailResponse result = service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        assertThat(result.summary().status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(result.summary().totalRequests()).isEqualTo(0);
    }

    @Test
    void startRun_singleRequest_executesAndRecords() {
        setupRunStart();
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        RunResultDetailResponse result = service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        assertThat(result.summary().status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(result.summary().totalRequests()).isEqualTo(1);
        assertThat(result.summary().passedRequests()).isEqualTo(1);
        verify(runIterationRepository).save(any(RunIteration.class));
    }

    @Test
    void startRun_multipleRequests_executesInOrder() {
        setupRunStart();
        Request req1 = buildRequest("First", HttpMethod.GET, "https://api.com/1", 0, rootFolder);
        Request req2 = buildRequest("Second", HttpMethod.POST, "https://api.com/2", 1, rootFolder);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of(rootFolder));
        when(requestRepository.findByFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of(req1, req2));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of());
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        RunResultDetailResponse result = service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        assertThat(result.summary().totalRequests()).isEqualTo(2);
        assertThat(result.summary().passedRequests()).isEqualTo(2);

        // Verify execution order
        ArgumentCaptor<RunIteration> captor = ArgumentCaptor.forClass(RunIteration.class);
        verify(runIterationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getRequestName()).isEqualTo("First");
        assertThat(captor.getAllValues().get(1).getRequestName()).isEqualTo("Second");
    }

    @Test
    void startRun_folderOrder_depthFirst() {
        setupRunStart();
        Folder folderA = buildFolder("A", 0, testCollection, null);
        Folder folderB = buildFolder("B", 1, testCollection, null);
        Request reqA = buildRequest("ReqA", HttpMethod.GET, "https://a.com", 0, folderA);
        Request reqB = buildRequest("ReqB", HttpMethod.GET, "https://b.com", 0, folderB);

        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of(folderA, folderB));
        when(requestRepository.findByFolderIdOrderBySortOrder(folderA.getId())).thenReturn(List.of(reqA));
        when(requestRepository.findByFolderIdOrderBySortOrder(folderB.getId())).thenReturn(List.of(reqB));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(folderA.getId())).thenReturn(List.of());
        when(folderRepository.findByParentFolderIdOrderBySortOrder(folderB.getId())).thenReturn(List.of());
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        ArgumentCaptor<RunIteration> captor = ArgumentCaptor.forClass(RunIteration.class);
        verify(runIterationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getRequestName()).isEqualTo("ReqA");
        assertThat(captor.getAllValues().get(1).getRequestName()).isEqualTo("ReqB");
    }

    @Test
    void startRun_nestedFolders_correctOrder() {
        setupRunStart();
        Folder parent = buildFolder("Parent", 0, testCollection, null);
        Folder child = buildFolder("Child", 0, testCollection, parent);
        Request reqParent = buildRequest("ParentReq", HttpMethod.GET, "https://p.com", 0, parent);
        Request reqChild = buildRequest("ChildReq", HttpMethod.GET, "https://c.com", 0, child);

        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of(parent));
        when(requestRepository.findByFolderIdOrderBySortOrder(parent.getId())).thenReturn(List.of(reqParent));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(parent.getId())).thenReturn(List.of(child));
        when(requestRepository.findByFolderIdOrderBySortOrder(child.getId())).thenReturn(List.of(reqChild));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(child.getId())).thenReturn(List.of());
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        ArgumentCaptor<RunIteration> captor = ArgumentCaptor.forClass(RunIteration.class);
        verify(runIterationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getRequestName()).isEqualTo("ParentReq");
        assertThat(captor.getAllValues().get(1).getRequestName()).isEqualTo("ChildReq");
    }

    @Test
    void startRun_withPreRequestScript_executesBeforeRequest() {
        setupRunStart();
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);

        // Script on the request
        RequestScript preScript = RequestScript.builder()
                .scriptType(ScriptType.PRE_REQUEST).content("pm.variables.set('key','val');")
                .request(testRequest).build();
        preScript.setId(UUID.randomUUID());
        when(requestScriptRepository.findByRequestIdAndScriptType(testRequest.getId(), ScriptType.PRE_REQUEST))
                .thenReturn(Optional.of(preScript));
        lenient().when(requestScriptRepository.findByRequestIdAndScriptType(testRequest.getId(), ScriptType.POST_RESPONSE))
                .thenReturn(Optional.empty());
        when(scriptEngineService.executePreRequestScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        lenient().when(scriptEngineService.executePostResponseScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> inv.getArgument(1));

        service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        // Pre-request script was called before proxy execution
        InOrder order = inOrder(scriptEngineService, requestProxyService);
        order.verify(scriptEngineService).executePreRequestScript(eq("pm.variables.set('key','val');"), any());
        order.verify(requestProxyService).executeRequest(any(), any(), any());
    }

    @Test
    void startRun_withPostResponseScript_executesAfterRequest() {
        setupRunStart();
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);

        RequestScript postScript = RequestScript.builder()
                .scriptType(ScriptType.POST_RESPONSE).content("pm.test('status', function(){});")
                .request(testRequest).build();
        postScript.setId(UUID.randomUUID());
        lenient().when(requestScriptRepository.findByRequestIdAndScriptType(testRequest.getId(), ScriptType.PRE_REQUEST))
                .thenReturn(Optional.empty());
        when(requestScriptRepository.findByRequestIdAndScriptType(testRequest.getId(), ScriptType.POST_RESPONSE))
                .thenReturn(Optional.of(postScript));
        lenient().when(scriptEngineService.executePreRequestScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(scriptEngineService.executePostResponseScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> inv.getArgument(1));

        service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        InOrder order = inOrder(requestProxyService, scriptEngineService);
        order.verify(requestProxyService).executeRequest(any(), any(), any());
        order.verify(scriptEngineService).executePostResponseScript(eq("pm.test('status', function(){});"), any());
    }

    @Test
    void startRun_scriptCancelsRequest_skipsExecution() {
        setupRunStart();
        setupSingleRequest();
        setupScriptPassthrough();

        // Pre-request script cancels
        when(scriptEngineService.executePreRequestScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> {
                    ScriptContext ctx = inv.getArgument(1);
                    ctx.setRequestCancelled(true);
                    return ctx;
                });
        testCollection.setPreRequestScript("pm.request.cancel();");

        service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        verify(requestProxyService, never()).executeRequest(any(), any(), any());
        ArgumentCaptor<RunIteration> captor = ArgumentCaptor.forClass(RunIteration.class);
        verify(runIterationRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).contains("Skipped");
    }

    @Test
    void startRun_collectionScript_runsForEveryRequest() {
        setupRunStart();
        testCollection.setPreRequestScript("console.log('pre');");
        Request req1 = buildRequest("Req1", HttpMethod.GET, "https://a.com", 0, rootFolder);
        Request req2 = buildRequest("Req2", HttpMethod.GET, "https://b.com", 1, rootFolder);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of(rootFolder));
        when(requestRepository.findByFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of(req1, req2));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of());
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        // Collection script called twice (once per request)
        verify(scriptEngineService, times(2)).executePreRequestScript(eq("console.log('pre');"), any());
    }

    @Test
    void startRun_folderScript_runsForFolderRequests() {
        setupRunStart();
        rootFolder.setPreRequestScript("console.log('folder');");
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        verify(scriptEngineService).executePreRequestScript(eq("console.log('folder');"), any());
    }

    @Test
    void startRun_withDelay_sleepsBetweenRequests() {
        setupRunStart();
        Request req1 = buildRequest("Req1", HttpMethod.GET, "https://a.com", 0, rootFolder);
        Request req2 = buildRequest("Req2", HttpMethod.GET, "https://b.com", 1, rootFolder);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of(rootFolder));
        when(requestRepository.findByFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of(req1, req2));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of());
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        RunResultDetailResponse result = service.startRun(buildRunRequest(1, 100), TEAM_ID, USER_ID);

        assertThat(result.summary().delayBetweenRequestsMs()).isEqualTo(100);
        assertThat(result.summary().totalRequests()).isEqualTo(2);
    }

    @Test
    void startRun_multipleIterations_repeatsAllRequests() {
        setupRunStart();
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        RunResultDetailResponse result = service.startRun(buildRunRequest(3, 0), TEAM_ID, USER_ID);

        assertThat(result.summary().totalRequests()).isEqualTo(3);
        assertThat(result.summary().iterationCount()).isEqualTo(3);
        verify(runIterationRepository, times(3)).save(any(RunIteration.class));
    }

    @Test
    void startRun_withDataFile_injectsVariables() {
        setupRunStart();
        setupSingleRequest();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        List<Map<String, String>> dataRows = List.of(
                Map.of("token", "abc"),
                Map.of("token", "xyz")
        );
        when(dataFileParser.parse("csv-content", "data.csv")).thenReturn(dataRows);

        // Variable service should receive the data variables
        when(variableService.resolveVariables(anyString(), any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(requestProxyService.resolveInheritedAuth(any()))
                .thenReturn(null);

        StartCollectionRunRequest runRequest = new StartCollectionRunRequest(
                COLLECTION_ID, ENV_ID, 1, 0, "data.csv", "csv-content");
        RunResultDetailResponse result = service.startRun(runRequest, TEAM_ID, USER_ID);

        assertThat(result.summary().totalRequests()).isEqualTo(2);
    }

    @Test
    void startRun_dataCycling_wrapsAroundDataRows() {
        setupRunStart();
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        List<Map<String, String>> dataRows = List.of(
                Map.of("env", "dev"),
                Map.of("env", "staging")
        );
        when(dataFileParser.parse("data", "data.csv")).thenReturn(dataRows);

        StartCollectionRunRequest runRequest = new StartCollectionRunRequest(
                COLLECTION_ID, ENV_ID, 4, 0, "data.csv", "data");
        RunResultDetailResponse result = service.startRun(runRequest, TEAM_ID, USER_ID);

        // 4 iterations with 2 data rows → cycles
        assertThat(result.summary().totalRequests()).isEqualTo(4);
        assertThat(result.summary().iterationCount()).isEqualTo(4);
    }

    @Test
    void startRun_failedRequest_markedAsFailed() {
        setupRunStart();
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(500, "Internal Server Error", 100);
        setupScriptPassthrough();

        RunResultDetailResponse result = service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        assertThat(result.summary().status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.summary().failedRequests()).isEqualTo(1);
        assertThat(result.summary().passedRequests()).isEqualTo(0);
    }

    @Test
    void startRun_assertionResults_capturedInIteration() {
        setupRunStart();
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);

        // Post-response script adds assertions
        lenient().when(scriptEngineService.executePreRequestScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(scriptEngineService.executePostResponseScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> {
                    ScriptContext ctx = inv.getArgument(1);
                    ctx.addAssertion("Status is 200", true, "");
                    ctx.addAssertion("Body check", false, "Expected 'ok' but got 'error'");
                    return ctx;
                });
        lenient().when(requestScriptRepository.findByRequestIdAndScriptType(any(), eq(ScriptType.PRE_REQUEST)))
                .thenReturn(Optional.empty());
        RequestScript postScript = RequestScript.builder()
                .scriptType(ScriptType.POST_RESPONSE).content("test()").request(testRequest).build();
        postScript.setId(UUID.randomUUID());
        when(requestScriptRepository.findByRequestIdAndScriptType(testRequest.getId(), ScriptType.POST_RESPONSE))
                .thenReturn(Optional.of(postScript));

        RunResultDetailResponse result = service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        assertThat(result.summary().totalAssertions()).isEqualTo(2);
        assertThat(result.summary().passedAssertions()).isEqualTo(1);
        assertThat(result.summary().failedAssertions()).isEqualTo(1);

        ArgumentCaptor<RunIteration> captor = ArgumentCaptor.forClass(RunIteration.class);
        verify(runIterationRepository).save(captor.capture());
        assertThat(captor.getValue().getAssertionResults()).contains("Status is 200");
        assertThat(captor.getValue().isPassed()).isFalse();
    }

    @Test
    void startRun_counters_correctlyTallied() {
        setupRunStart();
        Request req1 = buildRequest("Req1", HttpMethod.GET, "https://a.com", 0, rootFolder);
        Request req2 = buildRequest("Req2", HttpMethod.GET, "https://b.com", 1, rootFolder);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of(rootFolder));
        when(requestRepository.findByFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of(req1, req2));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of());
        setupVariablePassthrough();
        setupScriptPassthrough();

        // First request succeeds (200), second fails (500)
        when(requestProxyService.executeRequest(any(), any(), any()))
                .thenReturn(buildProxyResponse(200, "OK", 30))
                .thenReturn(buildProxyResponse(500, "Error", 70));

        RunResultDetailResponse result = service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        assertThat(result.summary().totalRequests()).isEqualTo(2);
        assertThat(result.summary().passedRequests()).isEqualTo(1);
        assertThat(result.summary().failedRequests()).isEqualTo(1);
        assertThat(result.summary().totalDurationMs()).isEqualTo(100);
    }

    @Test
    void startRun_status_completedOnSuccess() {
        setupRunStart();
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);
        setupScriptPassthrough();

        RunResultDetailResponse result = service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        assertThat(result.summary().status()).isEqualTo(RunStatus.COMPLETED);
    }

    @Test
    void startRun_status_failedOnFailure() {
        setupRunStart();
        setupSingleRequest();
        setupVariablePassthrough();
        setupProxyResponse(404, "Not Found", 50);
        setupScriptPassthrough();

        RunResultDetailResponse result = service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        assertThat(result.summary().status()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void startRun_variablesPersistAcrossRequests() {
        setupRunStart();
        Request req1 = buildRequest("Req1", HttpMethod.GET, "https://a.com", 0, rootFolder);
        Request req2 = buildRequest("Req2", HttpMethod.GET, "https://b.com", 1, rootFolder);
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of(rootFolder));
        when(requestRepository.findByFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of(req1, req2));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of());
        setupVariablePassthrough();
        setupProxyResponse(200, "OK", 50);

        // First request's post-response script sets a variable
        lenient().when(requestScriptRepository.findByRequestIdAndScriptType(any(), any()))
                .thenReturn(Optional.empty());
        testCollection.setPostResponseScript("pm.variables.set('token','abc123');");
        when(scriptEngineService.executePostResponseScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> {
                    ScriptContext ctx = inv.getArgument(1);
                    ctx.getLocalVariables().put("token", "abc123");
                    return ctx;
                });
        lenient().when(scriptEngineService.executePreRequestScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> inv.getArgument(1));

        service.startRun(buildRunRequest(1, 0), TEAM_ID, USER_ID);

        // Verify that variable resolution was called with the accumulated vars
        // The second request should receive the 'token' variable set by the first request's script
        verify(variableService, times(2)).resolveVariables(anyString(), any(), any(), any(), any());
    }

    // ─── CRUD Tests ───

    @Test
    void getRunResult_success() {
        RunResult runResult = buildRunResult(RunStatus.COMPLETED);
        when(runResultRepository.findById(runResult.getId())).thenReturn(Optional.of(runResult));

        RunResultResponse result = service.getRunResult(runResult.getId(), TEAM_ID);

        assertThat(result.id()).isEqualTo(runResult.getId());
    }

    @Test
    void getRunResult_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(runResultRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRunResult(id, TEAM_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getRunResult_wrongTeam_throws() {
        RunResult runResult = buildRunResult(RunStatus.COMPLETED);
        when(runResultRepository.findById(runResult.getId())).thenReturn(Optional.of(runResult));

        assertThatThrownBy(() -> service.getRunResult(runResult.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getRunResultDetail_includesIterations() {
        RunResult runResult = buildRunResult(RunStatus.COMPLETED);
        when(runResultRepository.findById(runResult.getId())).thenReturn(Optional.of(runResult));
        List<RunIteration> iterations = List.of(
                RunIteration.builder().iterationNumber(1).requestName("Test")
                        .requestMethod(HttpMethod.GET).requestUrl("https://test.com")
                        .passed(true).runResult(runResult).build());
        when(runIterationRepository.findByRunResultIdOrderByIterationNumber(runResult.getId()))
                .thenReturn(iterations);
        RunIterationResponse iterResp = new RunIterationResponse(
                UUID.randomUUID(), 1, "Test", HttpMethod.GET, "https://test.com",
                200, 50, 100, true, null, null);
        when(runResultMapper.toIterationResponseList(iterations)).thenReturn(List.of(iterResp));

        RunResultDetailResponse result = service.getRunResultDetail(runResult.getId(), TEAM_ID);

        assertThat(result.iterations()).hasSize(1);
        assertThat(result.iterations().get(0).requestName()).isEqualTo("Test");
    }

    @Test
    void getRunResults_byCollection() {
        RunResult r1 = buildRunResult(RunStatus.COMPLETED);
        RunResult r2 = buildRunResult(RunStatus.FAILED);
        when(runResultRepository.findByCollectionIdOrderByCreatedAtDesc(COLLECTION_ID))
                .thenReturn(List.of(r1, r2));

        List<RunResultResponse> results = service.getRunResults(COLLECTION_ID, TEAM_ID);

        assertThat(results).hasSize(2);
    }

    @Test
    void getRunResultsPaged_success() {
        RunResult r1 = buildRunResult(RunStatus.COMPLETED);
        Page<RunResult> page = new PageImpl<>(List.of(r1));
        when(runResultRepository.findByTeamId(eq(TEAM_ID), any(Pageable.class))).thenReturn(page);

        PageResponse<RunResultResponse> result = service.getRunResultsPaged(TEAM_ID, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void cancelRun_setsStatusCancelled() {
        RunResult runResult = buildRunResult(RunStatus.RUNNING);
        when(runResultRepository.findById(runResult.getId())).thenReturn(Optional.of(runResult));
        when(runResultRepository.save(any(RunResult.class))).thenAnswer(inv -> inv.getArgument(0));

        RunResultResponse result = service.cancelRun(runResult.getId(), TEAM_ID, USER_ID);

        assertThat(result.status()).isEqualTo(RunStatus.CANCELLED);
        assertThat(result.completedAt()).isNotNull();
    }

    @Test
    void cancelRun_notRunning_throws() {
        RunResult runResult = buildRunResult(RunStatus.COMPLETED);
        when(runResultRepository.findById(runResult.getId())).thenReturn(Optional.of(runResult));

        assertThatThrownBy(() -> service.cancelRun(runResult.getId(), TEAM_ID, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("running");
    }

    @Test
    void deleteRunResult_success() {
        RunResult runResult = buildRunResult(RunStatus.COMPLETED);
        when(runResultRepository.findById(runResult.getId())).thenReturn(Optional.of(runResult));

        service.deleteRunResult(runResult.getId(), TEAM_ID);

        verify(runResultRepository).delete(runResult);
    }

    @Test
    void collectRequestsInOrder_respectsSortOrder() {
        Folder folder = buildFolder("F", 0, testCollection, null);
        Request r1 = buildRequest("First", HttpMethod.GET, "https://1.com", 0, folder);
        Request r2 = buildRequest("Second", HttpMethod.GET, "https://2.com", 1, folder);
        Request r3 = buildRequest("Third", HttpMethod.GET, "https://3.com", 2, folder);

        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of(folder));
        when(requestRepository.findByFolderIdOrderBySortOrder(folder.getId()))
                .thenReturn(List.of(r1, r2, r3));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(folder.getId()))
                .thenReturn(List.of());

        List<Request> result = service.collectRequestsInOrder(COLLECTION_ID);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("First");
        assertThat(result.get(1).getName()).isEqualTo("Second");
        assertThat(result.get(2).getName()).isEqualTo("Third");
    }

    @Test
    void collectRequestsInOrder_emptyCollection() {
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of());

        List<Request> result = service.collectRequestsInOrder(COLLECTION_ID);

        assertThat(result).isEmpty();
    }

    // ─── Helpers ───

    private void setupRunStart() {
        when(collectionRepository.findById(COLLECTION_ID)).thenReturn(Optional.of(testCollection));
        when(runResultRepository.save(any(RunResult.class))).thenAnswer(inv -> {
            RunResult r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(runIterationRepository.findByRunResultIdOrderByIterationNumber(any())).thenReturn(List.of());
    }

    private void setupSingleRequest() {
        when(folderRepository.findByCollectionIdAndParentFolderIsNullOrderBySortOrder(COLLECTION_ID))
                .thenReturn(List.of(rootFolder));
        when(requestRepository.findByFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of(testRequest));
        when(folderRepository.findByParentFolderIdOrderBySortOrder(rootFolder.getId()))
                .thenReturn(List.of());
    }

    private void setupVariablePassthrough() {
        lenient().when(variableService.resolveVariables(anyString(), any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(requestProxyService.resolveInheritedAuth(any()))
                .thenReturn(null);
    }

    private void setupProxyResponse(int status, String statusText, long timeMs) {
        when(requestProxyService.executeRequest(any(), any(), any()))
                .thenReturn(buildProxyResponse(status, statusText, timeMs));
    }

    private void setupScriptPassthrough() {
        lenient().when(scriptEngineService.executePreRequestScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        lenient().when(scriptEngineService.executePostResponseScript(anyString(), any(ScriptContext.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        lenient().when(requestScriptRepository.findByRequestIdAndScriptType(any(), any()))
                .thenReturn(Optional.empty());
    }

    private ProxyResponse buildProxyResponse(int status, String statusText, long timeMs) {
        return new ProxyResponse(status, statusText, Map.of(), "{}", timeMs, 2, "application/json", List.of(), null);
    }

    private StartCollectionRunRequest buildRunRequest(int iterations, int delayMs) {
        return new StartCollectionRunRequest(COLLECTION_ID, ENV_ID, iterations, delayMs, null, null);
    }

    private Collection buildCollection(UUID id, UUID teamId) {
        Collection c = Collection.builder()
                .teamId(teamId).name("Test Collection").createdBy(USER_ID).build();
        c.setId(id);
        return c;
    }

    private Folder buildFolder(String name, int sortOrder, Collection collection, Folder parent) {
        Folder f = Folder.builder()
                .name(name).sortOrder(sortOrder).collection(collection).parentFolder(parent).build();
        f.setId(UUID.randomUUID());
        return f;
    }

    private Request buildRequest(String name, HttpMethod method, String url, int sortOrder, Folder folder) {
        Request r = Request.builder()
                .name(name).method(method).url(url).sortOrder(sortOrder).folder(folder).build();
        r.setId(UUID.randomUUID());
        return r;
    }

    private RunResult buildRunResult(RunStatus status) {
        RunResult r = RunResult.builder()
                .teamId(TEAM_ID).collectionId(COLLECTION_ID).environmentId(ENV_ID)
                .status(status).startedAt(Instant.now()).startedByUserId(USER_ID).build();
        r.setId(UUID.randomUUID());
        r.setCreatedAt(Instant.now());
        return r;
    }
}
