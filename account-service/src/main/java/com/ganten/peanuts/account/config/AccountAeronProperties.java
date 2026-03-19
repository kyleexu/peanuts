package com.ganten.peanuts.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "account.aeron")
public class AccountAeronProperties {

    private boolean enabled = true;
    private String channel = "aeron:ipc";
    private int lockRequestStreamId = 2101;
    private int lockResponseStreamId = 2102;
    private int tradeStreamId = 2003;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

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
