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

    /**
     * 创建已成功执行且有工具事实支撑的结果。
     *
     * @param operation 规范化操作名称
     * @param orderId 订单编号
     * @param userId 订单所属用户编号
     * @param message 返回给用户的执行结果
     * @return SUCCEEDED 结果
     */
    public static SensitiveOperationResult success(String operation, String orderId, String userId, String message) {
        return new SensitiveOperationResult(true, operation, orderId, userId, message, SUCCEEDED, null);
    }

    /**
     * 创建因本地参数或权限校验失败而未调用工具的结果。
     *
     * @param operation 规范化操作名称
     * @param orderId 可选订单编号
     * @param userId 可选用户编号
     * @param message 返回给用户的失败原因
     * @return 不具备事实支撑的 FAILED 结果
     */
    public static SensitiveOperationResult failure(String operation, String orderId, String userId, String message) {
        return new SensitiveOperationResult(false, operation, orderId, userId, message, FAILED, null);
    }

    /**
     * 创建订单服务已作出权威业务拒绝的结果。
     *
     * @param operation 规范化操作名称
     * @param orderId 订单编号
     * @param userId 订单所属用户编号
     * @param message 返回给用户的业务拒绝原因
     * @return 有工具事实支撑的 REJECTED 结果
     */
    public static SensitiveOperationResult rejected(String operation, String orderId, String userId, String message) {
        return new SensitiveOperationResult(false, operation, orderId, userId, message, REJECTED, null);
    }

    /**
     * 创建 MCP 或下游服务异常导致的技术失败结果，并保留原始异常供 Trace 记录。
     *
     * @param operation 规范化操作名称
     * @param orderId 订单编号
     * @param userId 订单所属用户编号
     * @param message 返回给用户的可恢复失败提示
     * @param error 原始技术异常
     * @return 携带异常的 FAILED 结果
     */
    public static SensitiveOperationResult technicalFailure(String operation, String orderId, String userId,
                                                            String message, Throwable error) {
        return new SensitiveOperationResult(false, operation, orderId, userId, message, FAILED, error);
    }

    /**
     * 判断结果是否由下游工具的成功或业务拒绝事实支撑。
     *
     * @return 成功或业务拒绝时返回 true，未执行和技术失败时返回 false
     */
    public boolean grounded() {
        return success || REJECTED.equals(outcome);
    }
}
