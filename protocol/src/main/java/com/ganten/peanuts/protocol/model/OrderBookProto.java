package com.ganten.peanuts.protocol.model;

import java.util.ArrayList;
import java.util.List;
import com.ganten.peanuts.common.enums.Contract;

import java.math.BigDecimal;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Raw order book snapshot encoded/decoded via Aeron transport.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderBookProto {

    private Contract contract;
    private long timestamp;

    private List<OrderLevel> bidOrders = new ArrayList<OrderLevel>();
    private List<OrderLevel> askOrders = new ArrayList<OrderLevel>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderLevel {
        private long orderId;
        private long userId;
        private BigDecimal price;
        private BigDecimal totalQuantity;
        private BigDecimal filledQuantity;
        private BigDecimal remainingQuantity;
        private long timestamp;
    }
}
