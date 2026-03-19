package com.ganten.peanuts.common.enums;

public enum TimeInForce {

    GTC(1), // Good Till Cancel：一直有效直到撤单
    IOC(2), // Immediate Or Cancel：立即成交剩余撤销
    FOK(3); // Fill Or Kill：要么全部成交要么全部取消

    private final int code;

    TimeInForce(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static TimeInForce fromCode(int code) {
        switch (code) {
            case 1:
                return GTC;
            case 2:
                return IOC;
            case 3:
                return FOK;
            default:
                throw new IllegalArgumentException("Invalid TimeInForce code: " + code);
        }
    }
}
