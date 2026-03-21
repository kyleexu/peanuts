package com.ganten.peanuts.account.model;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import com.ganten.peanuts.common.enums.Currency;

public class TransferIncreaseRequest {

    @NotNull
    private Long fromUserId;

    @NotNull
    private Long toUserId;

    @NotNull
    private Currency currency;

    @NotNull
    @DecimalMin("0.00000001")
    private BigDecimal amount;

    public Long getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Long fromUserId) {
        this.fromUserId = fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
