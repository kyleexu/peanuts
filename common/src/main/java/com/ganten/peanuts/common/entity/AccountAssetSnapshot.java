package com.ganten.peanuts.common.entity;

import java.math.BigDecimal;
import com.ganten.peanuts.common.enums.Currency;

public class AccountAssetSnapshot {

    private final long userId;
    private final Currency currency;
    private final BigDecimal available;
    private final BigDecimal locked;

    public AccountAssetSnapshot(long userId, Currency currency, BigDecimal available, BigDecimal locked) {
        this.userId = userId;
        this.currency = currency;
        this.available = available;
        this.locked = locked;
    }

    public long getUserId() {
        return userId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getLocked() {
        return locked;
    }
}
