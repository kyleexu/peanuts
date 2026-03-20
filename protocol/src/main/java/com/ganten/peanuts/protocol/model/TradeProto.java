package com.ganten.peanuts.protocol.model;

import java.math.BigDecimal;
import com.ganten.peanuts.common.enums.Contract;

public class TradeProto {

    private long tradeId;
    private long buyOrderId;
    private long sellOrderId;
    private long buyUserId;
    private long sellUserId;
    private Contract contract;
    private BigDecimal price;
    private BigDecimal quantity;
    private long timestamp;

    public long getTradeId() {
        return tradeId;
    }

    public void setTradeId(long tradeId) {
        this.tradeId = tradeId;
    }

    public long getBuyOrderId() {
        return buyOrderId;
    }

    public void setBuyOrderId(long buyOrderId) {
        this.buyOrderId = buyOrderId;
    }

    public long getSellOrderId() {
        return sellOrderId;
    }

    public void setSellOrderId(long sellOrderId) {
        this.sellOrderId = sellOrderId;
    }

    public long getBuyUserId() {
        return buyUserId;
    }

    public void setBuyUserId(long buyUserId) {
        this.buyUserId = buyUserId;
    }

    public long getSellUserId() {
        return sellUserId;
    }

    public void setSellUserId(long sellUserId) {
        this.sellUserId = sellUserId;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
