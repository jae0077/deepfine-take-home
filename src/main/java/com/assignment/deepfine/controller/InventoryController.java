package com.assignment.deepfine.controller;

import com.assignment.deepfine.dto.InboundRequest;
import com.assignment.deepfine.dto.OutboundRequest;
import com.assignment.deepfine.dto.ProductStock;
import com.assignment.deepfine.exception.ApiExceptionHandler.ErrorResponse;
import com.assignment.deepfine.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "입출고")
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
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    // 상품 입고
    @ApiResponse(responseCode = "200", description = "처리 성공", useReturnTypeSchema = true)
    @Operation(
            summary = "상품 입고",
            description =
                    "미등록 ID는 신규 등록합니다. 기존 ID의 상품명은 정확히 일치해야 하며 누적 재고는 BIGINT 상한을 넘을 수 없습니다. 응답은 입고 직후 재고입니다.")
    @ApiResponse(
            responseCode = "409",
            description = "PRODUCT_NAME_MISMATCH: 상품명 불일치 / STOCK_LIMIT_EXCEEDED: 재고 상한 초과",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/inbounds")
    public ProductStock inbound(@Valid @RequestBody InboundRequest request) {
        return service.inbound(request.id(), request.name(), request.amount());
    }

    // 상품 출고
    @ApiResponse(responseCode = "200", description = "처리 성공", useReturnTypeSchema = true)
    @Operation(summary = "상품 출고", description = "재고가 충분할 때만 수량을 차감합니다. 응답은 출고 직후 재고입니다.")
    @ApiResponse(
            responseCode = "404",
            description = "PRODUCT_NOT_FOUND: 미등록 상품",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "INSUFFICIENT_STOCK: 재고 부족",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/outbounds")
    public ProductStock outbound(@Valid @RequestBody OutboundRequest request) {
        return service.outbound(request.id(), request.amount());
    }
}
