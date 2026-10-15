package com.assignment.deepfine.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorResponse(String code, String message) {}

    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<ErrorResponse> business(InventoryException exception) {
        return switch (exception.reason()) {
            case INVALID_AMOUNT -> error(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "입출고 수량은 양수여야 합니다.");
            case PRODUCT_NOT_FOUND -> error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품이 없습니다.");
            case INSUFFICIENT_STOCK -> error(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "재고가 부족합니다.");
            case PRODUCT_NAME_MISMATCH -> error(HttpStatus.CONFLICT, "PRODUCT_NAME_MISMATCH", "등록된 상품명과 입고 상품명이 다릅니다.");
            case STOCK_LIMIT_EXCEEDED -> error(HttpStatus.CONFLICT, "STOCK_LIMIT_EXCEEDED", "재고 수량 상한을 초과합니다.");
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> bodyValidation(MethodArgumentNotValidException exception) {
        // 수량 검증 실패는 INVALID_AMOUNT로, 나머지 필드 오류는 INVALID_REQUEST로 구분한다.
        boolean invalidAmount = exception.getBindingResult().getFieldErrors().stream()
                .anyMatch(error -> error.getField().equals("amount"));
        return invalidAmount ? error(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "입출고 수량은 양의 정수여야 합니다.")
                : error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
    }

    // JSON 파싱·타입 오류, 경로/쿼리 검증 오류는 모두 400으로 통일한다.
    @ExceptionHandler({HttpMessageNotReadableException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 형식과 값을 확인해 주세요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "요청 처리 중 오류가 발생했습니다.");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
