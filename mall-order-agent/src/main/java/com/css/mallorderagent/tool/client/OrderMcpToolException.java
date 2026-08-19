package com.css.mallorderagent.tool.client;

/**
 * MCP 订单 Tool 调用异常。
 */
public class OrderMcpToolException extends RuntimeException {

    public OrderMcpToolException(String message) {
        super(message);
    }

    public OrderMcpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
