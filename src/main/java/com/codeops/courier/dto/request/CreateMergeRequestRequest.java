package com.codeops.courier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateMergeRequestRequest(
        @NotNull UUID forkId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description
) {}
