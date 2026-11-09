package com.css.mallorderagent.tool.client;

import java.util.List;

/**
 * MCP 售后 Tool 的稳定结果契约。业务拒绝是正常结果，技术故障仍通过异常传播。
 */
public record AfterSalesToolResult(
        boolean success,
        String message,
        String failureType,
        String decision,
        List<String> reasonCodes,
        List<String> missingFields,
        String nextAction,
        String policyVersion) {

    public AfterSalesToolResult {
        message = message != null ? message : "";
        reasonCodes = reasonCodes != null ? List.copyOf(reasonCodes) : List.of();
        missingFields = missingFields != null ? List.copyOf(missingFields) : List.of();
    }

    static AfterSalesToolResult legacySuccess(String message) {
        return new AfterSalesToolResult(true, message, null, null,
                List.of(), List.of(), null, null);
    }
}
