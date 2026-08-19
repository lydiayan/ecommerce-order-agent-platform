package com.example.mallorder.refund;

import java.util.Arrays;
import java.util.Optional;

public enum DeliveryStatus {
    NOT_SHIPPED(0),
    IN_TRANSIT(1),
    SIGNED(2),
    REJECTED(3);

    private final int code;

    DeliveryStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Optional<DeliveryStatus> fromCode(Integer code) {
        return Arrays.stream(values()).filter(value -> code != null && value.code == code).findFirst();
    }
}
