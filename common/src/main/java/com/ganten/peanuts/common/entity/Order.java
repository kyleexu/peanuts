package com.ganten.peanuts.common.entity;

import java.math.BigDecimal;
import com.ganten.peanuts.common.enums.*;
import lombok.Data;

@Data
public class Order {

    // 订单ID
    private long orderId;
    // 用户ID
    private long userId;
    // 订单状态
    private OrderStatus OrderStatus;

    // 交易合约
    private Contract contract;
    // 交易方向（买/卖）
    private Side side;

    // 订单类型（限价单/市价单等）
    private OrderType orderType;
    // 订单时效性（IOC/FOK/GTC等）
    private TimeInForce timeInForce;

    // 订单价格
    private BigDecimal price;
    // 已成交数量
    private BigDecimal filledQuantity;
    // 订单总数量
    private BigDecimal totalQuantity;

    // 订单时间戳
    private long timestamp;

    // 订单来源
    private Source source;
}
