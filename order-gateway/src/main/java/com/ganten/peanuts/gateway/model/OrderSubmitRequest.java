package com.ganten.peanuts.gateway.model;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import com.ganten.peanuts.common.enums.*;

/**
// {
//   "userId": 10001,
//   "contract": "BTC_USDT",
//   "side": "BUY",
//   "orderType": "LIMIT",
//   "timeInForce": "GTC",
//   "price": 45000,
//   "totalQuantity": 2,
//   "source": "API"
// }
 */
public class OrderSubmitRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Contract contract;

    @NotNull
    private Side side;

    @NotNull
    private OrderType orderType;

    @NotNull
    private TimeInForce timeInForce;

    @NotNull
    @DecimalMin("0.00000001")
    private BigDecimal price;

    @NotNull
    @DecimalMin("0.00000001")
    private BigDecimal totalQuantity;

    private Source source;

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
}
