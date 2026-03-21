package com.ganten.peanuts.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ganten.peanuts.protocol.aeron.AeronProperties;

@ConfigurationProperties(prefix = "account.aeron")
public class AccountAeronProperties extends AeronProperties {

    private int lockRequestStreamId = 2101;
    private int lockResponseStreamId = 2102;
    private int tradeStreamId = 2003;

    public int getLockRequestStreamId() {
        return lockRequestStreamId;
    }

    public void setLockRequestStreamId(int lockRequestStreamId) {
        this.lockRequestStreamId = lockRequestStreamId;
    }

    public int getLockResponseStreamId() {
        return lockResponseStreamId;
    }

    public void setLockResponseStreamId(int lockResponseStreamId) {
        this.lockResponseStreamId = lockResponseStreamId;
    }

    public int getTradeStreamId() {
        return tradeStreamId;
    }

    public void setTradeStreamId(int tradeStreamId) {
        this.tradeStreamId = tradeStreamId;
    }
}
