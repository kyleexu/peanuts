package com.ganten.peanuts.market.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.ganten.peanuts.common.enums.Contract;

public class OrderBookSnapshot {

    private Contract contract;
    private long timestamp;
    private int levelMultiplier;
    private BigDecimal levelStep;
    private List<OrderBookLevelSnapshot> bids = new ArrayList<OrderBookLevelSnapshot>();
    private List<OrderBookLevelSnapshot> asks = new ArrayList<OrderBookLevelSnapshot>();

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getLevelMultiplier() {
        return levelMultiplier;
    }

    public void setLevelMultiplier(int levelMultiplier) {
        this.levelMultiplier = levelMultiplier;
    }

    public BigDecimal getLevelStep() {
        return levelStep;
    }

    public void setLevelStep(BigDecimal levelStep) {
        this.levelStep = levelStep;
    }

    public List<OrderBookLevelSnapshot> getBids() {
        return bids;
    }

    public void setBids(List<OrderBookLevelSnapshot> bids) {
        this.bids = bids;
    }

    public List<OrderBookLevelSnapshot> getAsks() {
        return asks;
    }

    public void setAsks(List<OrderBookLevelSnapshot> asks) {
        this.asks = asks;
    }
}
