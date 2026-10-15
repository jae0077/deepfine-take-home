package com.assignment.deepfine.dto;

// 상품별 재고 결과
public record ProductStock(
        long id,
        String name,
        long quantity
) {}
