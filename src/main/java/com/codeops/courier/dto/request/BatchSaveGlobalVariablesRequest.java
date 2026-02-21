package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BatchSaveGlobalVariablesRequest(
        @NotNull List<SaveGlobalVariableRequest> variables
) {}
