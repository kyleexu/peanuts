package com.ganten.peanuts.protocol.model;

import java.math.BigDecimal;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.OrderAction;
import com.ganten.peanuts.common.enums.OrderType;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.common.enums.Source;
import com.ganten.peanuts.common.enums.TimeInForce;

public class OrderProto {

    private long orderId;
    private long userId;
    private Contract contract;
    private Side side;
    private OrderType orderType;
    private TimeInForce timeInForce;
    private BigDecimal price;
    private BigDecimal totalQuantity;
    private long timestamp;
    private Source source;
    private OrderAction action;
    private long targetOrderId;

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public TimeInForce getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(TimeInForce timeInForce) {
        this.timeInForce = timeInForce;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public OrderAction getAction() {
        return action;
    }

    public void setAction(OrderAction action) {
        this.action = action;
    }

    public long getTargetOrderId() {
        return targetOrderId;
    }

    public void setTargetOrderId(long targetOrderId) {
        this.targetOrderId = targetOrderId;
    }
}
