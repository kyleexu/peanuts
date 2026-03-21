package com.ganten.peanuts.account.model;

import com.ganten.peanuts.common.entity.AccountAssetSnapshot;

public class TransferResult {

    private boolean success;
    private AccountAssetSnapshot from;
    private AccountAssetSnapshot to;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public AccountAssetSnapshot getFrom() {
        return from;
    }

    public void setFrom(AccountAssetSnapshot from) {
        this.from = from;
    }

    public AccountAssetSnapshot getTo() {
        return to;
    }

    public void setTo(AccountAssetSnapshot to) {
        this.to = to;
    }
}
