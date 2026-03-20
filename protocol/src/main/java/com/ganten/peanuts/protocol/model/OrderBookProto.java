package com.ganten.peanuts.protocol.model;

import java.util.ArrayList;
import java.util.List;
import com.ganten.peanuts.common.enums.Contract;

import java.math.BigDecimal;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 这里的 OrderBookProto 是订单簿快照的协议模型，用于在 Aeron 中传输订单簿快照
 * 注意: 订单簿快照只包含订单ID、用户ID、价格、数量、已成交量、时间戳，并不是聚合后的订单簿
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderBookProto {

    private Contract contract; // 合约
    private long timestamp; // 时间戳，单位毫秒

    private List<OrderSnapshot> bidOrders = new ArrayList<OrderSnapshot>(); // 买盘列表，按照价格从高到低排序
    private List<OrderSnapshot> askOrders = new ArrayList<OrderSnapshot>(); // 卖盘列表，按照价格从低到高排序

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderSnapshot {
        private long orderId; // 订单ID
        private long userId; // 用户ID
        private BigDecimal price; // 价格
        private BigDecimal totalQuantity; // 总数量
        private BigDecimal filledQuantity; // 已成交量，filledQuantity = totalQuantity - remainingQuantity
        private BigDecimal remainingQuantity; // 剩余数量，remainingQuantity = totalQuantity - filledQuantity
        private long timestamp; // 时间戳，单位毫秒
    }
}
