package com.ganten.peanuts.market.model;

import java.io.Serializable;
import java.math.BigDecimal;

import com.ganten.peanuts.common.enums.Contract;

/**
 * Latest trade snapshot for websocket push.
 */
public class TradeSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private Contract contract;
    private BigDecimal price;
    private BigDecimal quantity;
    private long timestamp;

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
