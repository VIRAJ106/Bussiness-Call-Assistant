package com.dayclose.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateMultiLineReservationRequest(
        @NotNull UUID commitmentId,
        String customerRef,
        @NotEmpty @Valid List<ReservationLineRequest> lines,
        String ttl
) {}
