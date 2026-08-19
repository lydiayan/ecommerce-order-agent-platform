package com.example.mallorder.refund;

import java.util.Arrays;
import java.util.Optional;

public enum ProductType {
    ORDINARY(0),
    CUSTOMIZED(1),
    FRESH(2),
    VIRTUAL(3);

    private final int code;

    ProductType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Optional<ProductType> fromCode(Integer code) {
        return Arrays.stream(values()).filter(value -> code != null && value.code == code).findFirst();
    }
}
