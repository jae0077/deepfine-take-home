package com.assignment.deepfine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

// 상품 입출고 이력
@Entity
@Table(name = "inventory_history")
public class InventoryHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 상품 객체 전체를 로딩할 필요가 없어 ID만 매핑한다. 실제 FK는 DDL에 존재
    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(name = "type", nullable = false, length = 8)
    private String type;

    @Column(nullable = false)
    private long amount;

    // 예: 재고 10에서 3개 출고했다면 amount=3, quantityAfter=7이다.
    @Column(name = "quantity_after", nullable = false)
    private long quantityAfter;

    // DB 기본값으로 생성한 시각을 INSERT 후 엔티티에도 반영한다.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InventoryHistoryEntity() {}

    public InventoryHistoryEntity(long productId, String type, long amount, long quantityAfter) {
        this.productId = productId;
        this.type = type;
        this.amount = amount;
        this.quantityAfter = quantityAfter;
    }

    public Long getId() {
        return id;
    }

    public long getProductId() {
        return productId;
    }

    public String getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public long getQuantityAfter() {
        return quantityAfter;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
