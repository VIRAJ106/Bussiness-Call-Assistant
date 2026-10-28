package com.dayclose.commitment.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateCommitmentRequest(
        String customerRef,
        @NotNull String type,  // "order", "discount", "reorder"
        @NotEmpty @Valid List<CommitmentLineDto> lines,
        String metadata
) {
    public record CommitmentLineDto(
            @NotNull java.util.UUID itemId,
            @NotNull java.util.UUID locationId,
            java.util.UUID reservationId,
            @NotNull @Min(1) Integer qty,
            @NotNull @Min(0) Integer pricePaise
    ) {}
}
