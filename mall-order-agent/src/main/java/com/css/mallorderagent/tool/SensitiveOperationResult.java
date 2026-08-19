package com.css.mallorderagent.tool;

public record SensitiveOperationResult(
        boolean success,
        String operation,
        String orderId,
        String userId,
        String message) {

    public static SensitiveOperationResult success(String operation, String orderId, String userId, String message) {
        return new SensitiveOperationResult(true, operation, orderId, userId, message);
    }

    public static SensitiveOperationResult failure(String operation, String orderId, String userId, String message) {
        return new SensitiveOperationResult(false, operation, orderId, userId, message);
    }
}
