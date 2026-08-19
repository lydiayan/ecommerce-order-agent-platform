package com.example.mallorder.refund;

import java.util.Arrays;
import java.util.Optional;

public enum OrderStatus {
    PENDING_PAYMENT(0),
    PAID(1),
    SHIPPED(2),
    COMPLETED(3),
    CANCELLED(4);

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Optional<OrderStatus> fromCode(Integer code) {
        return Arrays.stream(values()).filter(value -> code != null && value.code == code).findFirst();
    }
}
