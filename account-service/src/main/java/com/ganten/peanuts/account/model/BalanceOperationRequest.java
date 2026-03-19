package com.ganten.peanuts.account.model;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import com.ganten.peanuts.common.enums.Currency;

public class BalanceOperationRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Currency currency;

    @NotNull
    @DecimalMin("0.00000001")
    private BigDecimal amount;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
