package com.assignment.deepfine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InboundRequest(
        @NotNull @Positive Long id,
        @NotBlank @Size(max = 200) String name,
        @NotNull @Positive Long amount) {}
