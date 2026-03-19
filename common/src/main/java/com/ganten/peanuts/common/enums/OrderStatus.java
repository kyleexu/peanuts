package com.ganten.peanuts.common.enums;

/**
 * 创建 → 进入撮合队列 → 部分成交 → 全部成交
 *                  ↘ 撤单
 *                   ↘ 拒单
 *                   ↘ 过期失效
 */
public enum OrderStatus {

    NEW(1), // 新订单（已进入系统）
    PARTIALLY_FILLED(2), // 部分成交
    FILLED(3), // 全部成交
    CANCELED(4), // 已撤单
    REJECTED(5), // 被拒单
    EXPIRED(6); // 过期失效（如IOC未成交部分）

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static OrderStatus fromCode(int code) {
        switch (code) {
            case 1:
                return NEW;
            case 2:
                return PARTIALLY_FILLED;
            case 3:
                return FILLED;
            case 4:
                return CANCELED;
            case 5:
                return REJECTED;
            case 6:
                return EXPIRED;
            default:
                throw new IllegalArgumentException("Invalid OrderStatus code: " + code);
        }
    }
}
