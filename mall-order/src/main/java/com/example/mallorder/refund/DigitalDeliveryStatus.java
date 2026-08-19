package com.example.mallorder.refund;

import java.util.Arrays;
import java.util.Optional;

public enum DigitalDeliveryStatus {
    NOT_DELIVERED(0),
    DELIVERED(1),
    REDEEMED(2);

    private final int code;

    DigitalDeliveryStatus(int code) {
        this.code = code;
    }

    public static Optional<DigitalDeliveryStatus> fromCode(Integer code) {
        return Arrays.stream(values()).filter(value -> code != null && value.code == code).findFirst();
    }
}
