package com.ganten.peanuts.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market.aeron")
public class MarketAeronProperties {

    private boolean enabled = true;
    private String channel = "aeron:ipc";
    private int tradeStreamId = 2003;
    private int orderBookStreamId = 2004;
    private int fragmentLimit = 100;

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

    public int getTradeStreamId() {
        return tradeStreamId;
    }

    public void setTradeStreamId(int tradeStreamId) {
        this.tradeStreamId = tradeStreamId;
    }

    public int getOrderBookStreamId() {
        return orderBookStreamId;
    }

    public void setOrderBookStreamId(int orderBookStreamId) {
        this.orderBookStreamId = orderBookStreamId;
    }

    public int getFragmentLimit() {
        return fragmentLimit;
    }

    public void setFragmentLimit(int fragmentLimit) {
        this.fragmentLimit = fragmentLimit;
    }
}
