package com.assignment.deepfine.controller;

import com.assignment.deepfine.dto.CursorPage;
import com.assignment.deepfine.dto.InventoryMovement;
import com.assignment.deepfine.dto.ProductStock;
import com.assignment.deepfine.dto.RenameProductRequest;
import com.assignment.deepfine.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final InventoryService service;

    public ProductController(InventoryService service) {
        this.service = service;
    }

    // 상품 이름 변경
    @PatchMapping("/{id}/name")
    public ProductStock rename(
            @PathVariable @Positive long id, @Valid @RequestBody RenameProductRequest request) {
        return service.rename(id, request.name());
    }

    // 전체 상품 재고 조회
    @GetMapping("/stock")
    public CursorPage<ProductStock> stocks(
            @RequestParam(required = false) @Positive Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CursorPage.from(service.stocks(cursor, size + 1), size, ProductStock::id);
    }

    // 상품 재고 조회
    @GetMapping("/{id}/stock")
    public ProductStock stock(@PathVariable @Positive long id) {
        return service.stock(id);
    }

    @GetMapping("/{id}/history")
    public CursorPage<InventoryMovement> history(
            @PathVariable @Positive long id,
            @RequestParam(required = false) @Positive Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CursorPage.from(service.history(id, cursor, size + 1), size, InventoryMovement::id);
    }
}
