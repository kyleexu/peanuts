package com.ganten.peanuts.market.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.ganten.peanuts.common.entity.PriceQuantity;
import com.ganten.peanuts.common.enums.Contract;

/**
 * 订单簿快照包含合约、时间戳、聚合级别信息，以及买卖盘的价格和数量列表
 */
public class OrderBookSnapshot {

    // 交易币对
    private Contract contract;
    // 快照生成时间戳，单位毫秒
    private long timestamp;
    // 聚合级别倍数，Constants.multiplierList 中的一个值，表示价格聚合的倍数
    private int multiplier;
    // 聚合级别 = contract.getPriceTick() * multiplier
    private BigDecimal levelStep;
    // 买盘列表，按照价格从高到低排序
    private List<PriceQuantity> bids = new ArrayList<PriceQuantity>();
    // 卖盘列表，按照价格从低到高排序
    private List<PriceQuantity> asks = new ArrayList<PriceQuantity>();

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

    public int getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(int multiplier) {
        this.multiplier = multiplier;
    }

    public BigDecimal getLevelStep() {
        return levelStep;
    }

    public void setLevelStep(BigDecimal levelStep) {
        this.levelStep = levelStep;
    }

    public List<PriceQuantity> getBids() {
        return bids;
    }

    public void setBids(List<PriceQuantity> bids) {
        this.bids = bids;
    }

    public List<PriceQuantity> getAsks() {
        return asks;
    }

    public void setAsks(List<PriceQuantity> asks) {
        this.asks = asks;
    }
}
