package com.ganten.peanuts.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.aeron")
public class AeronProperties {

    private boolean enabled = true;
    private String channel = "aeron:ipc";
    private int streamId = 2001;
    private int accountLockRequestStreamId = 2101;
    private int accountLockResponseStreamId = 2102;
    private long accountLockTimeoutMs = 500;

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

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    public int getAccountLockRequestStreamId() {
        return accountLockRequestStreamId;
    }

    public void setAccountLockRequestStreamId(int accountLockRequestStreamId) {
        this.accountLockRequestStreamId = accountLockRequestStreamId;
    }

    public int getAccountLockResponseStreamId() {
        return accountLockResponseStreamId;
    }

    public void setAccountLockResponseStreamId(int accountLockResponseStreamId) {
        this.accountLockResponseStreamId = accountLockResponseStreamId;
    }

    public long getAccountLockTimeoutMs() {
        return accountLockTimeoutMs;
    }

    public void setAccountLockTimeoutMs(long accountLockTimeoutMs) {
        this.accountLockTimeoutMs = accountLockTimeoutMs;
    }
}
