package com.ganten.peanuts.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ganten.peanuts.protocol.aeron.AeronProperties;

@ConfigurationProperties(prefix = "market.aeron")
public class MarketAeronProperties extends AeronProperties {

    private int tradeStreamId = 2003;
    private int orderBookStreamId = 2004;

    public MarketAeronProperties() {
        // keep previous default (market-service was 100)
        setFragmentLimit(100);
    }

    public int getOrderBookStreamId() {
        return orderBookStreamId;
    }

    public void setOrderBookStreamId(int orderBookStreamId) {
        this.orderBookStreamId = orderBookStreamId;
    }

    public int getTradeStreamId() {
        return tradeStreamId;
    }

    public void setTradeStreamId(int tradeStreamId) {
        this.tradeStreamId = tradeStreamId;
    }
}
