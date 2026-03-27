package com.ganten.peanuts.maker.model;

import java.math.BigDecimal;

import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.OrderAction;
import com.ganten.peanuts.common.enums.OrderType;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.common.enums.Source;
import com.ganten.peanuts.common.enums.TimeInForce;

public class OrderSubmitRequest {

    private Long orderId;
    private Long userId;
    private Contract contract;
    private Side side;
    private OrderType orderType;
    private TimeInForce timeInForce;
    private BigDecimal price;
    private BigDecimal totalQuantity;
    private Source source;
    private OrderAction action;
    private Long targetOrderId;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
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

    public Long getTargetOrderId() {
        return targetOrderId;
    }

    public void setTargetOrderId(Long targetOrderId) {
        this.targetOrderId = targetOrderId;
    }
}
