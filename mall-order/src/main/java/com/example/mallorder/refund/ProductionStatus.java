package com.example.mallorder.refund;

import java.util.Arrays;
import java.util.Optional;

public enum ProductionStatus {
    NOT_STARTED(0),
    IN_PROGRESS(1),
    COMPLETED(2);

    private final int code;

    ProductionStatus(int code) {
        this.code = code;
    }

    public static Optional<ProductionStatus> fromCode(Integer code) {
        return Arrays.stream(values()).filter(value -> code != null && value.code == code).findFirst();
    }
}
