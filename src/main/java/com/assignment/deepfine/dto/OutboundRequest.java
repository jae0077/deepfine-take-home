package com.assignment.deepfine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OutboundRequest(
        @Schema(description = "상품 고유 ID", example = "1") @NotNull @Positive Long id,
        @Schema(description = "입출고 수량. 양의 정수", example = "10") @NotNull @Positive Long amount) {}
