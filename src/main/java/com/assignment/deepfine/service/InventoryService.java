package com.assignment.deepfine.service;

import com.assignment.deepfine.exception.InventoryException;
import com.assignment.deepfine.dto.InventoryMovement;
import com.assignment.deepfine.dto.ProductStock;
import com.assignment.deepfine.entity.InventoryHistoryEntity;
import com.assignment.deepfine.entity.ProductEntity;
import com.assignment.deepfine.repository.ProductRepository;
import com.assignment.deepfine.repository.InventoryHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.assignment.deepfine.exception.InventoryException.Reason.*;

@Service
public class InventoryService {
    private final ProductRepository productRepository;
    private final InventoryHistoryRepository historyRepository;

    public InventoryService(ProductRepository productRepository, InventoryHistoryRepository historyRepository) {
        this.productRepository = productRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProductStock inbound(long id, String name, long amount) {
        validateAmount(amount);
        // 이름 불일치나 수량 상한 때문에 갱신되지 않으면, 잠긴 행을 조회해 오류를 분류한다.
        ProductEntity product = productRepository.inbound(id, name, amount, Long.MAX_VALUE)
                .orElseThrow(() -> inboundFailure(id, name));
        // 입고 직후의 재고를 기록한다.
        historyRepository.save(new InventoryHistoryEntity(product.getId(), "INBOUND", amount, product.getQuantity()));
        return convertToStock(product);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProductStock outbound(long id, long amount) {
        validateAmount(amount);
        // 조건부 UPDATE로 출고 시도 후 결과가 없을 때만 상품 존재 여부를 확인해 실패 원인 구분한다.
        ProductEntity product = productRepository.outbound(id, amount).orElseThrow(() ->
                new InventoryException(productRepository.existsById(id) ? INSUFFICIENT_STOCK : PRODUCT_NOT_FOUND));
        // 출고 직후 재고를 기록한다.
        historyRepository.save(new InventoryHistoryEntity(product.getId(), "OUTBOUND", amount, product.getQuantity()));
        return convertToStock(product);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProductStock rename(long id, String name) {
        return convertToStock(productRepository.rename(id, name)
                .orElseThrow(() -> new InventoryException(PRODUCT_NOT_FOUND)));
    }

    // SQL 결과가 비어있을때 동작
    private InventoryException inboundFailure(long id, String name) {
        ProductEntity current = productRepository.findById(id)
                .orElseThrow(() -> new InventoryException(PRODUCT_NOT_FOUND));
        // UPSERT에서 변경되지 않은 원인을 기존 상품명과 비교해 구분한다.
        return new InventoryException(
                current.getName().equals(name)
                        ? STOCK_LIMIT_EXCEEDED
                        : PRODUCT_NAME_MISMATCH
        );
    }

    @Transactional(readOnly = true)
    public ProductStock stock(long id) {
        return convertToStock(productRepository.findById(id)
                .orElseThrow(() -> new InventoryException(PRODUCT_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public List<ProductStock> stocks(Long cursor, int limit) {
        var page = PageRequest.of(0, limit);
        List<ProductEntity> products = cursor == null ? productRepository.findAllByOrderByIdAsc(page)
                : productRepository.findByIdGreaterThanOrderByIdAsc(cursor, page);
        return products.stream().map(this::convertToStock).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryMovement> history(long id, Long cursor, int limit) {
        // 존재하지 않는 상품일때 예외 응답
        if (!productRepository.existsById(id)) {
            throw new InventoryException(PRODUCT_NOT_FOUND);
        }
        var page = PageRequest.of(0, limit);
        List<InventoryHistoryEntity> history = cursor == null ? historyRepository.findByProductIdOrderByIdDesc(id, page)
                : historyRepository.findByProductIdAndIdLessThanOrderByIdDesc(id, cursor, page);
        return history.stream().map(this::convertToMovement).toList();
    }

    private ProductStock convertToStock(ProductEntity product) {
        return new ProductStock(product.getId(), product.getName(), product.getQuantity());
    }

    private InventoryMovement convertToMovement(InventoryHistoryEntity history) {
        return new InventoryMovement(history.getId(), history.getProductId(), history.getType(),
                history.getAmount(), history.getQuantityAfter(), history.getCreatedAt());
    }

    // 음수나 0이 들어오는 경우 예외 응답
    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InventoryException(INVALID_AMOUNT);
        }
    }
}
