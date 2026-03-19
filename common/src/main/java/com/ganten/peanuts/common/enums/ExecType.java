package com.ganten.peanuts.common.enums;

public enum ExecType {

    TRADE(1), // 成交
    CANCELED(2), // 已撤单
    REJECTED(3); // 被拒单（风控/余额不足等）

    private final int code;

    ExecType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ExecType fromCode(int code) {
        switch (code) {
            case 1:
                return TRADE;
            case 2:
                return CANCELED;
            case 3:
                return REJECTED;
            default:
                throw new IllegalArgumentException("Invalid ExecType code: " + code);
        }
    }
}
