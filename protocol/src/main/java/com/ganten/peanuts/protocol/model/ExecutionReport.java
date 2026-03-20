package com.ganten.peanuts.protocol.model;

import java.math.BigDecimal;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.ExecType;
import com.ganten.peanuts.common.enums.OrderStatus;
import com.ganten.peanuts.common.enums.Side;

public class ExecutionReport {

    private long orderId;
    private long counterpartyOrderId;
    private long userId;
    private Contract contract;
    private Side side;
    private ExecType execType;
    private OrderStatus orderStatus;
    private BigDecimal matchedPrice;
    private BigDecimal matchedQuantity;
    private long timestamp;

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public long getCounterpartyOrderId() {
        return counterpartyOrderId;
    }

    public void setCounterpartyOrderId(long counterpartyOrderId) {
        this.counterpartyOrderId = counterpartyOrderId;
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

    public ExecType getExecType() {
        return execType;
    }

    public void setExecType(ExecType execType) {
        this.execType = execType;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public BigDecimal getMatchedPrice() {
        return matchedPrice;
    }

    public void setMatchedPrice(BigDecimal matchedPrice) {
        this.matchedPrice = matchedPrice;
    }

    public BigDecimal getMatchedQuantity() {
        return matchedQuantity;
    }

    public void setMatchedQuantity(BigDecimal matchedQuantity) {
        this.matchedQuantity = matchedQuantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
