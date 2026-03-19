package com.ganten.peanuts.common.enums;

public enum OrderAction {

    NEW(1), MODIFY(2), CANCEL(3);

    private final int code;

    OrderAction(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static OrderAction fromCode(int code) {
        switch (code) {
            case 1:
                return NEW;
            case 2:
                return MODIFY;
            case 3:
                return CANCEL;
            default:
                throw new IllegalArgumentException("Invalid OrderAction code: " + code);
        }
    }
}
