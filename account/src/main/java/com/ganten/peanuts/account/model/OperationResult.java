package com.ganten.peanuts.account.model;

import com.ganten.peanuts.common.entity.AccountAssetSnapshot;

public class OperationResult {

    private boolean success;
    private AccountAssetSnapshot balance;

    public OperationResult() {}

    public OperationResult(boolean success, AccountAssetSnapshot balance) {
        this.success = success;
        this.balance = balance;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public AccountAssetSnapshot getBalance() {
        return balance;
    }

    public void setBalance(AccountAssetSnapshot balance) {
        this.balance = balance;
    }
}
