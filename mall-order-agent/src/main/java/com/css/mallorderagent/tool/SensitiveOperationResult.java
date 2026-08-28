package com.css.mallorderagent.tool;

public record SensitiveOperationResult(
        boolean success,
        String operation,
        String orderId,
        String userId,
        String message,
        String outcome,
        Throwable error) {

    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String REJECTED = "REJECTED";
    public static final String FAILED = "FAILED";

    public static SensitiveOperationResult success(String operation, String orderId, String userId, String message) {
        return new SensitiveOperationResult(true, operation, orderId, userId, message, SUCCEEDED, null);
    }

    public static SensitiveOperationResult failure(String operation, String orderId, String userId, String message) {
        return new SensitiveOperationResult(false, operation, orderId, userId, message, FAILED, null);
    }

    public static SensitiveOperationResult rejected(String operation, String orderId, String userId, String message) {
        return new SensitiveOperationResult(false, operation, orderId, userId, message, REJECTED, null);
    }

    public static SensitiveOperationResult technicalFailure(String operation, String orderId, String userId,
                                                            String message, Throwable error) {
        return new SensitiveOperationResult(false, operation, orderId, userId, message, FAILED, error);
    }

    public boolean grounded() {
        return success || REJECTED.equals(outcome);
    }
}
