package com.assignment.deepfine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 상품명 변경 요청
public record RenameProductRequest(@NotBlank @Size(max = 200) String name) {}
