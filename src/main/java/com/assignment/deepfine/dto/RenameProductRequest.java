package com.assignment.deepfine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 상품명 변경 요청
public record RenameProductRequest(
        @Schema(description = "상품명. 공백만 입력할 수 없으며 최대 200자", example = "상품 A")
                @NotBlank
                @Size(max = 200)
                String name) {}
