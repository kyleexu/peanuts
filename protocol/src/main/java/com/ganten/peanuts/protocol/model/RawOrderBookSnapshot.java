package com.ganten.peanuts.protocol.model;

import java.util.ArrayList;
import java.util.List;
import com.ganten.peanuts.common.entity.OrderSnapshot;
import com.ganten.peanuts.common.enums.Contract;

/**
 * Raw order book snapshot encoded/decoded via Aeron transport.
 */
public class RawOrderBookSnapshot {

    private Contract contract;
    private long timestamp;
    private List<OrderSnapshot> bidOrders = new ArrayList<OrderSnapshot>();
    private List<OrderSnapshot> askOrders = new ArrayList<OrderSnapshot>();

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

    public List<OrderSnapshot> getBidOrders() {
        return bidOrders;
    }

    public void setBidOrders(List<OrderSnapshot> bidOrders) {
        this.bidOrders = bidOrders;
    }

    public List<OrderSnapshot> getAskOrders() {
        return askOrders;
    }

    public void setAskOrders(List<OrderSnapshot> askOrders) {
        this.askOrders = askOrders;
    }
}
