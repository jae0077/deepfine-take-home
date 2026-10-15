package com.assignment.deepfine.repository;

import com.assignment.deepfine.entity.ProductEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    // 이름 일치와 수량 상한 검사를 UPSERT에 포함해 동시 신규 등록·입고를 처리한다.
    @NativeQuery(
            """
            INSERT INTO products AS p (id, name, quantity)
            VALUES (:id, :name, :amount)
            ON CONFLICT (id) DO UPDATE
            SET quantity = p.quantity + EXCLUDED.quantity
            WHERE p.name = EXCLUDED.name
              AND p.quantity <= :maxQuantity - EXCLUDED.quantity
            RETURNING id, name, quantity
            """)
    Optional<ProductEntity> inbound(
            @Param("id") long id,
            @Param("name") String name,
            @Param("amount") long amount,
            @Param("maxQuantity") long maxQuantity);

    // 검사와 차감을 한 문장으로 실행한다. 미등록·재고 부족이면 빈 Optional을 반환한다.
    @NativeQuery(
            """
            UPDATE products SET quantity = quantity - :amount
            WHERE id = :id AND quantity >= :amount
            RETURNING id, name, quantity
            """)
    Optional<ProductEntity> outbound(@Param("id") long id, @Param("amount") long amount);

    // 같은 행을 변경하는 입출고와 행 잠금으로 조정된다. 수량은 유지한다.
    @NativeQuery(
            """
            UPDATE products SET name = :name WHERE id = :id
            RETURNING id, name, quantity
            """)
    Optional<ProductEntity> rename(@Param("id") long id, @Param("name") String name);

    List<ProductEntity> findAllByOrderByIdAsc(Pageable pageable);

    List<ProductEntity> findByIdGreaterThanOrderByIdAsc(long cursor, Pageable pageable);
}
