package com.ganten.peanuts.common.entity;

import java.math.BigDecimal;

public class PriceQuantity {

    private BigDecimal price;
    private BigDecimal quantity;

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
}
