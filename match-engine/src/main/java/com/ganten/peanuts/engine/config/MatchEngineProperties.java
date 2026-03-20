package com.ganten.peanuts.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ganten.peanuts.protocol.aeron.AeronProperties;

@ConfigurationProperties(prefix = "match-engine.aeron")
public class MatchEngineProperties extends AeronProperties {

    private int inboundStreamId = 2001;
    private int outboundStreamId = 2002;
    private int tradeStreamId = 2003;
    private int orderBookStreamId = 2004;

    public int getInboundStreamId() {
        return inboundStreamId;
    }

    public void setInboundStreamId(int inboundStreamId) {
        this.inboundStreamId = inboundStreamId;
    }

    public int getOutboundStreamId() {
        return outboundStreamId;
    }

    public void setOutboundStreamId(int outboundStreamId) {
        this.outboundStreamId = outboundStreamId;
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
}
