package com.ganten.peanuts.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.common.entity.OrderSnapshot;
import com.ganten.peanuts.common.entity.PriceQuantity;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.market.model.MarketMessage;
import com.ganten.peanuts.market.model.OrderBookSnapshot;
import com.ganten.peanuts.market.websocket.WebSocketBroadcaster;
import com.ganten.peanuts.protocol.model.RawOrderBookSnapshot;

@Service
public class OrderBookAggregationService {

    private static final int DEFAULT_LEVEL = 1;

    private final Map<Contract, RawOrderBookSnapshot> rawSnapshots =
            new ConcurrentHashMap<Contract, RawOrderBookSnapshot>();
    private final Map<String, OrderBookSnapshot> snapshotsByLevel = new ConcurrentHashMap<String, OrderBookSnapshot>();
    private final WebSocketBroadcaster webSocketBroadcaster;

    public OrderBookAggregationService(WebSocketBroadcaster webSocketBroadcaster) {
        this.webSocketBroadcaster = webSocketBroadcaster;
    }

    /**
     * 消息处理入口
     */
    public void onOrderBook(RawOrderBookSnapshot snapshot) {
        if (snapshot == null || snapshot.getContract() == null) {
            return;
        }

        rawSnapshots.put(snapshot.getContract(), snapshot);

        for (int levelMultiplier : Constants.multiplierList) {
            OrderBookSnapshot aggregated = aggregateByLevel(snapshot, levelMultiplier);
            snapshotsByLevel.put(key(snapshot.getContract(), levelMultiplier), aggregated);
        }

        OrderBookSnapshot defaultSnapshot = snapshotsByLevel.get(key(snapshot.getContract(), DEFAULT_LEVEL));
        if (defaultSnapshot != null) {
            webSocketBroadcaster.send(MarketMessage.ofOrderBook(defaultSnapshot));
        }
    }

    /**
     * controller 请求入口
     */
    public OrderBookSnapshot orderBook(Contract contract, int multiplier) {
        if (contract == null) {
            return null;
        }
        int normalizedLevel = this.normalizeMultiplier(multiplier);
        OrderBookSnapshot cached = snapshotsByLevel.get(key(contract, normalizedLevel));
        if (cached != null) {
            return cached;
        }
        RawOrderBookSnapshot raw = rawSnapshots.get(contract);
        if (raw == null) {
            return null;
        }
        OrderBookSnapshot aggregated = aggregateByLevel(raw, normalizedLevel);
        snapshotsByLevel.put(key(contract, normalizedLevel), aggregated);
        return aggregated;
    }

    private OrderBookSnapshot aggregateByLevel(RawOrderBookSnapshot raw, int levelMultiplier) {
        BigDecimal tickSize = BigDecimal.valueOf(raw.getContract().getTickSize());
        BigDecimal levelStep = tickSize.multiply(new BigDecimal(levelMultiplier));

        Map<BigDecimal, BigDecimal> bidLevels = new TreeMap<BigDecimal, BigDecimal>(Collections.reverseOrder());
        for (OrderSnapshot order : raw.getBidOrders()) {
            if (order.getPrice() == null || order.getRemainingQuantity() == null
                    || order.getRemainingQuantity().signum() <= 0) {
                continue;
            }
            BigDecimal bucketPrice = toBucketPrice(order.getPrice(), levelStep, true);
            bidLevels.merge(bucketPrice, order.getRemainingQuantity(), BigDecimal::add);
        }

        Map<BigDecimal, BigDecimal> askLevels = new TreeMap<BigDecimal, BigDecimal>();
        for (OrderSnapshot order : raw.getAskOrders()) {
            if (order.getPrice() == null || order.getRemainingQuantity() == null
                    || order.getRemainingQuantity().signum() <= 0) {
                continue;
            }
            BigDecimal bucketPrice = toBucketPrice(order.getPrice(), levelStep, false);
            askLevels.merge(bucketPrice, order.getRemainingQuantity(), BigDecimal::add);
        }

        OrderBookSnapshot aggregated = new OrderBookSnapshot();
        aggregated.setContract(raw.getContract());
        aggregated.setTimestamp(raw.getTimestamp());
        aggregated.setMultiplier(levelMultiplier);
        aggregated.setLevelStep(levelStep);
        aggregated.setBids(toLevels(bidLevels));
        aggregated.setAsks(toLevels(askLevels));
        return aggregated;
    }

    private int normalizeMultiplier(int multiplier) {
        if (Constants.multiplierList.contains(multiplier)) {
            return multiplier;
        }
        return DEFAULT_LEVEL;
    }

    private BigDecimal toBucketPrice(BigDecimal price, BigDecimal step, boolean bidSide) {
        if (step == null || step.signum() <= 0) {
            return price;
        }
        BigDecimal quotient = price.divide(step, 0, bidSide ? RoundingMode.FLOOR : RoundingMode.CEILING);
        return quotient.multiply(step);
    }

    private List<PriceQuantity> toLevels(Map<BigDecimal, BigDecimal> map) {
        List<PriceQuantity> result = new ArrayList<PriceQuantity>(map.size());
        for (Map.Entry<BigDecimal, BigDecimal> entry : map.entrySet()) {
            PriceQuantity level = new PriceQuantity();
            level.setPrice(entry.getKey());
            level.setQuantity(entry.getValue());
            result.add(level);
        }
        return result;
    }

    private String key(Contract contract, int multiplier) {
        return contract.name() + ":" + multiplier;
    }
}
