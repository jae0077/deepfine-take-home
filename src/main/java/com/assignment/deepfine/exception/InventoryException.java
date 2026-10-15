package com.assignment.deepfine.exception;

public class InventoryException extends RuntimeException {
    public enum Reason {
        INVALID_AMOUNT,
        PRODUCT_NOT_FOUND,
        INSUFFICIENT_STOCK,
        STOCK_LIMIT_EXCEEDED,
        PRODUCT_NAME_MISMATCH
    }

    private final Reason reason;

    public InventoryException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
