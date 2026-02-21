package com.codeops.courier.dto.response;

import java.util.List;

public record RunResultDetailResponse(
        RunResultResponse summary,
        List<RunIterationResponse> iterations
) {}
