package com.assignment.deepfine.dto;

import java.time.OffsetDateTime;

// 이력 조회 결과
public record InventoryMovement(
        long id,
        long productId,
        String type, // 입출고 타입
        long amount, // 변경량
        long quantityAfter, // 입출구 직후 재고
        OffsetDateTime createdAt
) {}
