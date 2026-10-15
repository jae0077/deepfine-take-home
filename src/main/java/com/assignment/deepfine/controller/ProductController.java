package com.assignment.deepfine.controller;

import com.assignment.deepfine.dto.CursorPage;
import com.assignment.deepfine.dto.InventoryMovement;
import com.assignment.deepfine.dto.ProductStock;
import com.assignment.deepfine.dto.RenameProductRequest;
import com.assignment.deepfine.exception.ApiExceptionHandler.ErrorResponse;
import com.assignment.deepfine.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "상품")
@ApiResponse(
        responseCode = "400",
        description =
                "INVALID_REQUEST: 요청 형식·상품 ID·상품명·페이징 값 오류 / INVALID_AMOUNT: 입출고 수량 누락 또는 0 이하",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(
        responseCode = "500",
        description = "INTERNAL_ERROR: 서버 처리 실패. 입출고와 이력 저장은 함께 롤백됩니다.",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final InventoryService service;

    public ProductController(InventoryService service) {
        this.service = service;
    }

    // 상품 이름 변경
    @ApiResponse(responseCode = "200", description = "처리 성공", useReturnTypeSchema = true)
    @Operation(summary = "상품명 변경", description = "상품명만 변경하며 입출고 이력을 생성하지 않습니다.")
    @ApiResponse(
            responseCode = "404",
            description = "PRODUCT_NOT_FOUND: 미등록 상품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}/name")
    public ProductStock rename(
            @Parameter(description = "상품 ID", example = "1") @PathVariable @Positive long id,
            @Valid @RequestBody RenameProductRequest request) {
        return service.rename(id, request.name());
    }

    // 전체 상품 재고 조회
    @ApiResponse(responseCode = "200", description = "처리 성공", useReturnTypeSchema = true)
    @Operation(
            summary = "전체 상품 재고 조회",
            description =
                    "상품 ID 오름차순입니다. 첫 요청은 cursor를 생략하고, 다음 요청은 응답의 nextCursor를 전달합니다. nextCursor가 null이면 마지막 페이지입니다.")
    @GetMapping("/stock")
    public CursorPage<ProductStock> stocks(
            @Parameter(description = "이전 페이지 응답의 nextCursor. 첫 조회 시 생략")
                    @RequestParam(required = false)
                    @Positive
                    Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CursorPage.from(service.stocks(cursor, size + 1), size, ProductStock::id);
    }

    // 상품 재고 조회
    @ApiResponse(responseCode = "200", description = "처리 성공", useReturnTypeSchema = true)
    @Operation(summary = "상품 재고 조회", description = "상품의 현재 재고를 조회합니다.")
    @ApiResponse(
            responseCode = "404",
            description = "PRODUCT_NOT_FOUND: 미등록 상품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}/stock")
    public ProductStock stock(
            @Parameter(description = "상품 ID", example = "1") @PathVariable @Positive long id) {
        return service.stock(id);
    }

    @ApiResponse(responseCode = "200", description = "처리 성공", useReturnTypeSchema = true)
    @Operation(
            summary = "입출고 이력 조회",
            description =
                    "이력 ID 내림차순으로 최근 생성된 이력부터 조회합니다. 다음 페이지는 응답의 nextCursor를 전달하며 null이면 마지막 페이지입니다.")
    @ApiResponse(
            responseCode = "404",
            description = "PRODUCT_NOT_FOUND: 미등록 상품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}/history")
    public CursorPage<InventoryMovement> history(
            @Parameter(description = "상품 ID", example = "1") @PathVariable @Positive long id,
            @Parameter(description = "이전 페이지 응답의 nextCursor. 첫 조회 시 생략")
                    @RequestParam(required = false)
                    @Positive
                    Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CursorPage.from(service.history(id, cursor, size + 1), size, InventoryMovement::id);
    }
}
