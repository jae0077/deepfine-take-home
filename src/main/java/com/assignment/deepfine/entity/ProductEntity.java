package com.assignment.deepfine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 상품
@Entity
@Table(name = "products")
public class ProductEntity {
    @Id private Long id; // 자동 생성 ID가 아니라 입고 요청에서 받은 상품 ID를 사용한다.

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private long quantity;

    protected ProductEntity() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getQuantity() {
        return quantity;
    }
}
