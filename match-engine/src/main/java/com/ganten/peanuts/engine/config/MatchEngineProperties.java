package com.ganten.peanuts.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import io.aeron.CommonContext;

@ConfigurationProperties(prefix = "match-engine.aeron")
public class MatchEngineProperties {

    private boolean enabled = true;
    private boolean launchEmbeddedDriver = true;
    private String channel = "aeron:ipc";
    private String directory = CommonContext.getAeronDirectoryName();
    private int inboundStreamId = 2001;
    private int outboundStreamId = 2002;
    private int tradeStreamId = 2003;
    private int orderBookStreamId = 2004;
    private int fragmentLimit = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLaunchEmbeddedDriver() {
        return launchEmbeddedDriver;
    }

    public void setLaunchEmbeddedDriver(boolean launchEmbeddedDriver) {
        this.launchEmbeddedDriver = launchEmbeddedDriver;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

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

    public int getFragmentLimit() {
        return fragmentLimit;
    }

    public void setFragmentLimit(int fragmentLimit) {
        this.fragmentLimit = fragmentLimit;
    }
}
