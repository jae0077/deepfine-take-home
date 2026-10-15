package com.assignment.deepfine.repository;

import com.assignment.deepfine.entity.InventoryHistoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryHistoryRepository extends JpaRepository<InventoryHistoryEntity, Long> {
    List<InventoryHistoryEntity> findByProductIdOrderByIdDesc(long productId, Pageable pageable);

    List<InventoryHistoryEntity> findByProductIdAndIdLessThanOrderByIdDesc(long productId, long cursor, Pageable pageable);
}
