package com.dayclose.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLocationRequest(
        @NotBlank String name
) {}
