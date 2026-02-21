package com.codeops.courier.dto.request;

import jakarta.validation.constraints.Size;

public record CreateForkRequest(
        @Size(max = 200) String label
) {}
