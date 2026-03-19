package com.ganten.peanuts.common.enums;

public enum OrderType {

    LIMIT(1), MARKET(2);

    private final int code;

    OrderType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static OrderType fromCode(int code) {
        switch (code) {
            case 1:
                return LIMIT;
            case 2:
                return MARKET;
            default:
                throw new IllegalArgumentException("Invalid OrderType code: " + code);
        }
    }
}
