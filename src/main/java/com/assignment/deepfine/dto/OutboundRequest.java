package com.assignment.deepfine.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OutboundRequest(
        @NotNull @Positive Long id,
        @NotNull @Positive Long amount
) {}
