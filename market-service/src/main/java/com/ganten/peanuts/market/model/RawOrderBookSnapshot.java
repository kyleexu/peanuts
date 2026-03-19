package com.ganten.peanuts.market.model;

import java.util.ArrayList;
import java.util.List;
import com.ganten.peanuts.common.enums.Contract;

public class RawOrderBookSnapshot {

    private Contract contract;
    private long timestamp;
    private List<OrderBookOrderSnapshot> bidOrders = new ArrayList<OrderBookOrderSnapshot>();
    private List<OrderBookOrderSnapshot> askOrders = new ArrayList<OrderBookOrderSnapshot>();

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

    public List<OrderBookOrderSnapshot> getBidOrders() {
        return bidOrders;
    }

    public void setBidOrders(List<OrderBookOrderSnapshot> bidOrders) {
        this.bidOrders = bidOrders;
    }

    public List<OrderBookOrderSnapshot> getAskOrders() {
        return askOrders;
    }

    public void setAskOrders(List<OrderBookOrderSnapshot> askOrders) {
        this.askOrders = askOrders;
    }
}
