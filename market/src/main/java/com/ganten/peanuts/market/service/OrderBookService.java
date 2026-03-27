package com.ganten.peanuts.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.common.entity.PriceQuantity;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.market.model.MarketMessage;
import com.ganten.peanuts.market.model.OrderBookSnapshot;
import com.ganten.peanuts.market.websocket.WebSocketBroadcaster;
import com.ganten.peanuts.protocol.model.OrderBookProto;
import com.ganten.peanuts.protocol.model.OrderBookProto.OrderSnapshot;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderBookService {

    private static final int DEFAULT_LEVEL = 1;

    private final Map<Contract, OrderBookProto> rawSnapshots = new ConcurrentHashMap<Contract, OrderBookProto>();
    private final Map<String, OrderBookSnapshot> snapshotsByLevel = new ConcurrentHashMap<String, OrderBookSnapshot>();
    private final WebSocketBroadcaster webSocketBroadcaster;

    public OrderBookService(WebSocketBroadcaster webSocketBroadcaster) {
        this.webSocketBroadcaster = webSocketBroadcaster;
    }

    /**
     * 消息处理入口
     */
    public void onOrderBook(OrderBookProto snapshot) {
        if (snapshot == null || snapshot.getContract() == null) {
            return;
        }
        log.info("onOrderBook: {}", snapshot);

        /**
         * 第 12 步，将订单簿快照存储到 rawSnapshots 中，用于后续的订单簿聚合
         * 然后按照 Constants.multiplierList 中的倍数进行订单簿聚合，并存储到 snapshotsByLevel 中。
         * 最后，将默认级别的订单簿快照推送到 WebSocket 客户端。
         */
        rawSnapshots.put(snapshot.getContract(), snapshot);

        /**
         * 第 13 步，按照 Constants.multiplierList 中的倍数进行订单簿聚合，并存储到 snapshotsByLevel 中。
         * 关键: 这里需要使用 aggregateByLevel 方法进行订单簿聚合
         */
        for (int levelMultiplier : Constants.multiplierList) {
            OrderBookSnapshot aggregated = aggregateByLevel(snapshot, levelMultiplier);
            snapshotsByLevel.put(key(snapshot.getContract(), levelMultiplier), aggregated);
        }

        /**
         * 第 14 步，将所有聚合级别的订单簿快照推送到 WebSocket 客户端。
         * 这样客户端可按 orderbook.<multiplier> 精确订阅。
         */
        for (int levelMultiplier : Constants.multiplierList) {
            OrderBookSnapshot levelSnapshot = snapshotsByLevel.get(key(snapshot.getContract(), levelMultiplier));
            if (levelSnapshot != null) {
                webSocketBroadcaster.send(MarketMessage.ofOrderBook(levelSnapshot));
            }
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
        OrderBookProto raw = rawSnapshots.get(contract);
        if (raw == null) {
            return null;
        }
        OrderBookSnapshot aggregated = aggregateByLevel(raw, normalizedLevel);
        snapshotsByLevel.put(key(contract, normalizedLevel), aggregated);
        return aggregated;
    }

    private OrderBookSnapshot aggregateByLevel(OrderBookProto raw, int levelMultiplier) {
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
