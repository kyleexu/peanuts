package com.ganten.peanuts.maker.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.Currency;
import com.ganten.peanuts.common.enums.OrderAction;
import com.ganten.peanuts.common.enums.OrderType;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.common.enums.Source;
import com.ganten.peanuts.common.enums.TimeInForce;
import com.ganten.peanuts.maker.cache.TickerCache;
import com.ganten.peanuts.maker.client.AccountClient;
import com.ganten.peanuts.maker.client.OrderClient;
import com.ganten.peanuts.maker.entity.LadderOrderRef;
import com.ganten.peanuts.maker.model.OrderSubmitRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MakerOrderScheduler {

    private final boolean enabled;
    private final AccountClient accountClient;
    private final OrderClient orderClient;
    private final TickerCache tickerCache;
    private final int orderBookLevel;
    private final BigDecimal minAvailableQuote;
    private final BigDecimal minAvailableBase;
    private final int ladderLevels;
    private final BigDecimal ladderStepBps;
    private final BigDecimal minLadderNotionalUsdt;
    private final BigDecimal maxLadderNotionalUsdt;
    private final int overlapLevels;
    private final BigDecimal overlapBps;
    private final long balanceWarnIntervalMs;
    private final Map<Contract, BigDecimal> anchors = new ConcurrentHashMap<Contract, BigDecimal>();
    private final Map<String, BigDecimal> balanceCache = new ConcurrentHashMap<String, BigDecimal>();
    private final Map<String, Long> balanceWarnAt = new ConcurrentHashMap<String, Long>();
    private final Map<Contract, List<LadderOrderRef>> liveLadderOrders =
            new ConcurrentHashMap<Contract, List<LadderOrderRef>>();

    private static final BigDecimal MIN_QTY = new BigDecimal("0.000001");
    private static final BigDecimal CANCEL_DUMMY = new BigDecimal("0.00000001");

    private static final long[] MAKER_USERS = {10001L, 10002L, 10003L};
    private static final long[] TAKER_USERS = {20001L, 20002L};

    private final AtomicInteger makerCursor = new AtomicInteger(0);
    private final AtomicInteger takerCursor = new AtomicInteger(0);

    public MakerOrderScheduler(
            AccountClient accountClient,
            OrderClient orderClient,
            TickerCache tickerCache,
            @Value("${maker.random-order.enabled:true}") boolean enabled,
            @Value("${maker.random-order.orderbook-level:1}") int orderBookLevel,
            @Value("${maker.random-order.min-available-quote:1000}") BigDecimal minAvailableQuote,
            @Value("${maker.random-order.min-available-base:0.5}") BigDecimal minAvailableBase,
            @Value("${maker.random-order.ladder-levels:6}") int ladderLevels,
            @Value("${maker.random-order.ladder-step-bps:8}") BigDecimal ladderStepBps,
            @Value("${maker.random-order.min-ladder-notional-usdt:120}") BigDecimal minLadderNotionalUsdt,
            @Value("${maker.random-order.max-ladder-notional-usdt:400}") BigDecimal maxLadderNotionalUsdt,
            @Value("${maker.random-order.overlap-levels:2}") int overlapLevels,
            @Value("${maker.random-order.overlap-bps:2}") BigDecimal overlapBps,
            @Value("${maker.random-order.balance-warn-interval-ms:30000}") long balanceWarnIntervalMs) {
        this.accountClient = accountClient;
        this.orderClient = orderClient;
        this.tickerCache = tickerCache;
        this.enabled = enabled;
        this.orderBookLevel = orderBookLevel;
        this.minAvailableQuote = minAvailableQuote;
        this.minAvailableBase = minAvailableBase;
        this.ladderLevels = Math.max(2, ladderLevels);
        this.ladderStepBps = ladderStepBps == null ? BigDecimal.valueOf(8) : ladderStepBps.max(BigDecimal.ONE);
        this.minLadderNotionalUsdt = minLadderNotionalUsdt == null
                ? BigDecimal.valueOf(120)
                : minLadderNotionalUsdt.max(BigDecimal.TEN);
        this.maxLadderNotionalUsdt = maxLadderNotionalUsdt == null
                ? BigDecimal.valueOf(400)
                : maxLadderNotionalUsdt.max(this.minLadderNotionalUsdt);
        this.overlapLevels = Math.max(0, overlapLevels);
        this.overlapBps = overlapBps == null ? BigDecimal.valueOf(2) : overlapBps.max(BigDecimal.ZERO);
        this.balanceWarnIntervalMs = Math.max(1000L, balanceWarnIntervalMs);

        anchors.put(Contract.BTC_USDT, BigDecimal.valueOf(45000));
        anchors.put(Contract.ETH_USDT, BigDecimal.valueOf(3000));
        seedBalanceCache();
    }

    @Scheduled(fixedDelayString = "${maker.random-order.fixed-delay-ms:1000}")
    public void dispatchRandomOrder() {
        if (!enabled) {
            return;
        }

        try {
            Contract contract = ThreadLocalRandom.current().nextBoolean() ? Contract.BTC_USDT : Contract.ETH_USDT;
            BigDecimal anchor = moveAnchor(contract);
            emitLadderOrders(contract, anchor);
        } catch (Exception ex) {
            log.warn("maker scheduler skipped one tick: {}", ex.getMessage());
        }
    }

    private BigDecimal moveAnchor(Contract contract) {
        BigDecimal current = anchors.get(contract);
        int bps = ThreadLocalRandom.current().nextInt(-8, 9);
        BigDecimal factor = BigDecimal.valueOf(10000 + bps).divide(BigDecimal.valueOf(10000));
        BigDecimal moved = current.multiply(factor);

        if (contract == Contract.BTC_USDT) {
            moved = clamp(moved, BigDecimal.valueOf(30000), BigDecimal.valueOf(70000));
        } else {
            moved = clamp(moved, BigDecimal.valueOf(1500), BigDecimal.valueOf(5000));
        }

        moved = moved.setScale(4, RoundingMode.HALF_UP);
        anchors.put(contract, moved);
        return moved;
    }

    private void emitLadderOrders(Contract contract, BigDecimal anchor) {
        long buyUser = this.selectBestUserByAvailable(MAKER_USERS, contract.getQuote(), makerCursor, minAvailableQuote);
        long sellUser = this.selectBestUserByAvailable(TAKER_USERS, contract.getBase(), takerCursor, minAvailableBase);

        BigDecimal price = resolvePriceFromOrderBook(contract, anchor);
        if (price == null || price.signum() <= 0) {
            log.warn("Skip maker tick, invalid price. contract={}", contract);
            return;
        }

        BigDecimal buyQuoteAvailable = fetchAvailableBalance(buyUser, contract.getQuote());
        BigDecimal sellBaseAvailable = fetchAvailableBalance(sellUser, contract.getBase());
        if (buyQuoteAvailable == null || sellBaseAvailable == null) {
            log.warn("Skip maker tick, failed to fetch balances. contract={}, buyUser={}, sellUser={}",
                    contract, buyUser, sellUser);
            return;
        }

        cancelExistingLadderOrders(contract);
        List<OrderSubmitRequest> orders = buildLadderOrders(
                contract, buyUser, sellUser, price, buyQuoteAvailable, sellBaseAvailable);
        if (orders.isEmpty()) {
            log.info("Skip maker tick, no valid ladder orders. contract={}, buyUser={}, sellUser={}, buyQuoteAvailable={}, sellBaseAvailable={}, price={}",
                    contract, buyUser, sellUser, buyQuoteAvailable, sellBaseAvailable, price);
            return;
        }

        List<LadderOrderRef> refs = new ArrayList<LadderOrderRef>(orders.size());
        for (OrderSubmitRequest order : orders) {
            submitOrder(order);
            refs.add(new LadderOrderRef(order.getOrderId(), order.getUserId(), order.getContract()));
        }
        liveLadderOrders.put(contract, refs);
    }

    private List<OrderSubmitRequest> buildLadderOrders(Contract contract,
            long buyUser,
            long sellUser,
            BigDecimal midPrice,
            BigDecimal buyQuoteAvailable,
            BigDecimal sellBaseAvailable) {
        List<OrderSubmitRequest> orders = new ArrayList<OrderSubmitRequest>();
        BigDecimal totalBuyQtyBudget = buyQuoteAvailable.divide(midPrice, 6, RoundingMode.DOWN);
        BigDecimal remainingBuyQty = totalBuyQtyBudget.max(BigDecimal.ZERO);
        BigDecimal remainingSellQty = sellBaseAvailable.max(BigDecimal.ZERO);
        BigDecimal oneBps = BigDecimal.valueOf(10000);

        for (int level = 1; level <= ladderLevels; level++) {
            BigDecimal bpsOffset = ladderStepBps.multiply(BigDecimal.valueOf(level));
            BigDecimal buyFactor = oneBps.subtract(bpsOffset).divide(oneBps, 8, RoundingMode.HALF_UP);
            BigDecimal sellFactor = oneBps.add(bpsOffset).divide(oneBps, 8, RoundingMode.HALF_UP);
            if (level <= overlapLevels && overlapBps.signum() > 0) {
                BigDecimal overlap = overlapBps.multiply(BigDecimal.valueOf(overlapLevels - level + 1));
                buyFactor = oneBps.add(overlap).divide(oneBps, 8, RoundingMode.HALF_UP);
                sellFactor = oneBps.subtract(overlap).divide(oneBps, 8, RoundingMode.HALF_UP);
            }
            if (buyFactor.signum() <= 0) {
                continue;
            }

            BigDecimal buyPrice = midPrice.multiply(buyFactor).setScale(4, RoundingMode.HALF_UP);
            BigDecimal sellPrice = midPrice.multiply(sellFactor).setScale(4, RoundingMode.HALF_UP);
            BigDecimal levelNotional = randomNotionalForLevel(level);

            BigDecimal buyQty = levelNotional.divide(buyPrice, 6, RoundingMode.DOWN);
            buyQty = buyQty.min(remainingBuyQty).max(BigDecimal.ZERO).setScale(6, RoundingMode.DOWN);
            if (buyQty.compareTo(MIN_QTY) >= 0) {
                orders.add(this.buildOrder(buyUser, contract, Side.BUY, buyPrice, buyQty));
                remainingBuyQty = remainingBuyQty.subtract(buyQty).max(BigDecimal.ZERO);
            }

            BigDecimal sellQty = levelNotional.divide(sellPrice, 6, RoundingMode.DOWN);
            sellQty = sellQty.min(remainingSellQty).max(BigDecimal.ZERO).setScale(6, RoundingMode.DOWN);
            if (sellQty.compareTo(MIN_QTY) >= 0) {
                orders.add(this.buildOrder(sellUser, contract, Side.SELL, sellPrice, sellQty));
                remainingSellQty = remainingSellQty.subtract(sellQty).max(BigDecimal.ZERO);
            }
        }
        return orders;
    }

    private BigDecimal randomNotionalForLevel(int level) {
        int rand = ThreadLocalRandom.current().nextInt(70, 131);
        BigDecimal noise = BigDecimal.valueOf(rand).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal center = BigDecimal.valueOf(ladderLevels + 1).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal distance = BigDecimal.valueOf(level).subtract(center).abs();
        BigDecimal normalized = distance.divide(center, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        BigDecimal curve = BigDecimal.ONE.subtract(normalized.multiply(new BigDecimal("0.65")));
        BigDecimal range = maxLadderNotionalUsdt.subtract(minLadderNotionalUsdt);
        BigDecimal baseline = minLadderNotionalUsdt.add(range.multiply(curve));
        return baseline.multiply(noise).setScale(4, RoundingMode.HALF_UP);
    }

    private void cancelExistingLadderOrders(Contract contract) {
        List<LadderOrderRef> refs = liveLadderOrders.remove(contract);
        if (refs == null || refs.isEmpty()) {
            return;
        }

        for (LadderOrderRef ref : refs) {
            try {
                submitOrder(buildCancelOrder(ref));
            } catch (Exception ex) {
                log.debug("Cancel old ladder order failed. contract={}, orderId={}, error={}",
                        contract, ref.getOrderId(), ex.getMessage());
            }
        }
    }

    private OrderSubmitRequest buildCancelOrder(LadderOrderRef ref) {
        OrderSubmitRequest cancel = new OrderSubmitRequest();
        cancel.setOrderId(System.nanoTime());
        cancel.setUserId(ref.getUserId());
        cancel.setContract(ref.getContract());
        cancel.setSide(Side.BUY);
        cancel.setOrderType(OrderType.LIMIT);
        cancel.setTimeInForce(TimeInForce.GTC);
        cancel.setPrice(CANCEL_DUMMY);
        cancel.setTotalQuantity(CANCEL_DUMMY);
        cancel.setSource(Source.SCHEDULER);
        cancel.setAction(OrderAction.CANCEL);
        cancel.setTargetOrderId(ref.getOrderId());
        return cancel;
    }

    private void submitOrder(OrderSubmitRequest order) {
        log.info("Submit ladder order. contract={}, side={}, price={}, quantity={}, userId={}",
                order.getContract(), order.getSide(), order.getPrice(), order.getTotalQuantity(), order.getUserId());
        orderClient.submitOrder(order);
    }

    private OrderSubmitRequest buildOrder(long userId, Contract contract, Side side, BigDecimal price, BigDecimal quantity) {
        OrderSubmitRequest order = new OrderSubmitRequest();
        order.setOrderId(System.nanoTime());
        order.setUserId(userId);
        order.setContract(contract);
        order.setSide(side);
        order.setOrderType(OrderType.LIMIT);
        order.setTimeInForce(TimeInForce.GTC);
        order.setPrice(price);
        order.setTotalQuantity(quantity);
        order.setSource(Source.SCHEDULER);
        order.setAction(OrderAction.NEW);
        order.setTargetOrderId(0L);
        return order;
    }

    private long pick(long[] users) {
        return users[ThreadLocalRandom.current().nextInt(users.length)];
    }

    private long selectBestUserByAvailable(long[] users, Currency currency, AtomicInteger cursor, BigDecimal minThreshold) {
        if (users == null || users.length == 0) {
            return -1L;
        }
        int start = Math.floorMod(cursor.getAndIncrement(), users.length);
        long bestUser = -1L;
        BigDecimal bestAvailable = BigDecimal.valueOf(-1);
        for (int i = 0; i < users.length; i++) {
            long userId = users[(start + i) % users.length];
            BigDecimal available = fetchAvailableBalance(userId, currency);
            if (available == null) {
                continue;
            }
            if (minThreshold != null && available.compareTo(minThreshold) < 0) {
                continue;
            }
            if (available.compareTo(bestAvailable) > 0) {
                bestAvailable = available;
                bestUser = userId;
            }
        }
        if (bestUser > 0L) {
            return bestUser;
        }
        return pick(users);
    }

    private BigDecimal resolvePriceFromOrderBook(Contract contract, BigDecimal fallbackAnchor) {
        BigDecimal cachedPrice = tickerCache.getLastPrice(contract);
        if (cachedPrice != null && cachedPrice.signum() > 0) {
            return cachedPrice.setScale(4, RoundingMode.HALF_UP);
        }
        return fallbackAnchor;
    }

    private BigDecimal fetchAvailableBalance(long userId, Currency currency) {
        String key = cacheKey(userId, currency);
        BigDecimal value = accountClient.fetchAvailableBalance(userId, currency);
        if (value != null) {
            balanceCache.put(key, value);
            return value;
        }
        BigDecimal cached = balanceCache.get(key);
        if (shouldWarnBalanceFallback(key)) {
            log.warn("Read account balance failed, fallback cache. userId={}, currency={}, cached={}",
                    userId, currency, cached);
        }
        return cached;
    }

    private boolean shouldWarnBalanceFallback(String cacheKey) {
        long now = System.currentTimeMillis();
        Long last = balanceWarnAt.get(cacheKey);
        if (last == null || now - last.longValue() >= balanceWarnIntervalMs) {
            balanceWarnAt.put(cacheKey, now);
            return true;
        }
        return false;
    }

    private String cacheKey(long userId, Currency currency) {
        return userId + ":" + currency.name();
    }

    private void seedBalanceCache() {
        for (long maker : MAKER_USERS) {
            balanceCache.put(cacheKey(maker, Currency.USDT), BigDecimal.valueOf(1_000_000L));
            balanceCache.put(cacheKey(maker, Currency.BTC), BigDecimal.valueOf(100L));
            balanceCache.put(cacheKey(maker, Currency.ETH), BigDecimal.valueOf(1000L));
        }
        for (long taker : TAKER_USERS) {
            balanceCache.put(cacheKey(taker, Currency.USDT), BigDecimal.valueOf(2_000_000L));
            balanceCache.put(cacheKey(taker, Currency.BTC), BigDecimal.valueOf(200L));
            balanceCache.put(cacheKey(taker, Currency.ETH), BigDecimal.valueOf(2000L));
        }
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }
}
