package com.assignment.deepfine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InboundRequest(
        @Schema(description = "상품 고유 ID", example = "1") @NotNull @Positive Long id,
        @Schema(description = "상품명. 공백만 입력할 수 없으며 최대 200자", example = "상품 A")
                @NotBlank
                @Size(max = 200)
                String name,
        @Schema(description = "입출고 수량. 양의 정수", example = "10") @NotNull @Positive Long amount) {}
