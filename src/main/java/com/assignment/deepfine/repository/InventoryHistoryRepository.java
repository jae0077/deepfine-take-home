package com.assignment.deepfine.repository;

import com.assignment.deepfine.entity.InventoryHistoryEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryHistoryRepository extends JpaRepository<InventoryHistoryEntity, Long> {
    List<InventoryHistoryEntity> findByProductIdOrderByIdDesc(long productId, Pageable pageable);

    List<InventoryHistoryEntity> findByProductIdAndIdLessThanOrderByIdDesc(
            long productId, long cursor, Pageable pageable);
}
