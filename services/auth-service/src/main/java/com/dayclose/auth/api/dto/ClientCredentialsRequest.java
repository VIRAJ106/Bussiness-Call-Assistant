package com.dayclose.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ClientCredentialsRequest(
        @NotBlank String clientId,
        @NotBlank String clientSecret
) {}
