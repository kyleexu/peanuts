package com.ganten.peanuts.maker.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.enums.*;
import com.ganten.peanuts.maker.cache.BalanceCache;
import com.ganten.peanuts.maker.cache.OrderExecutionStateCache;
import com.ganten.peanuts.maker.cache.TickerCache;
import com.ganten.peanuts.maker.client.MarketClient;
import com.ganten.peanuts.maker.client.OrderClient;
import com.ganten.peanuts.maker.model.OrderSubmitRequest;
import com.ganten.peanuts.maker.util.OrderIdGenerator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TakerFlowScheduler {

    private static final BigDecimal MIN_QTY = new BigDecimal("0.000001");
    private static final long[] TAKER_USERS = {20001L, 20002L};
    private static final List<Contract> CONTRACTS = Arrays.asList(Contract.BTC_USDT);

    private final boolean enabled;
    private final boolean takerEnabled;
    private final BalanceCache balanceCache;
    private final OrderClient orderClient;
    private final OrderExecutionStateCache orderExecutionStateCache;
    private final MarketClient marketClient;
    private final TickerCache tickerCache;

    private final BigDecimal minAvailableQuote;
    private final BigDecimal minAvailableBase;
    private final BigDecimal minTakerNotionalUsdt;
    private final BigDecimal maxTakerNotionalUsdt;
    private final BigDecimal takerSweepBps;
    private final long takerResultTimeoutMs;

    private final Map<Contract, BigDecimal> anchors = new ConcurrentHashMap<Contract, BigDecimal>();
    private final Map<Contract, BigDecimal> sideBiasByContract = new ConcurrentHashMap<Contract, BigDecimal>();
    private final Map<Long, Long> pendingTakerOrders = new ConcurrentHashMap<Long, Long>();
    private final AtomicInteger takerCursor = new AtomicInteger(0);

    public TakerFlowScheduler(BalanceCache balanceCache, OrderClient orderClient,
            OrderExecutionStateCache orderExecutionStateCache, MarketClient marketClient, TickerCache tickerCache,
            @Value("${maker.random-order.enabled}") boolean enabled,
            @Value("${maker.random-order.taker-enabled}") boolean takerEnabled,
            @Value("${maker.random-order.min-available-quote}") BigDecimal minAvailableQuote,
            @Value("${maker.random-order.min-available-base}") BigDecimal minAvailableBase,
            @Value("${maker.random-order.min-taker-notional-usdt}") BigDecimal minTakerNotionalUsdt,
            @Value("${maker.random-order.max-taker-notional-usdt}") BigDecimal maxTakerNotionalUsdt,
            @Value("${maker.random-order.taker-sweep-bps}") BigDecimal takerSweepBps,
            @Value("${maker.random-order.taker-result-timeout-ms}") long takerResultTimeoutMs) {
        this.balanceCache = balanceCache;
        this.orderClient = orderClient;
        this.orderExecutionStateCache = orderExecutionStateCache;
        this.marketClient = marketClient;
        this.tickerCache = tickerCache;
        this.enabled = enabled;
        this.takerEnabled = takerEnabled;
        this.minAvailableQuote = minAvailableQuote;
        this.minAvailableBase = minAvailableBase;
        this.minTakerNotionalUsdt =
                minTakerNotionalUsdt == null ? BigDecimal.valueOf(30) : minTakerNotionalUsdt.max(BigDecimal.ONE);
        this.maxTakerNotionalUsdt = maxTakerNotionalUsdt == null ? BigDecimal.valueOf(180)
                : maxTakerNotionalUsdt.max(this.minTakerNotionalUsdt);
        this.takerSweepBps = takerSweepBps == null ? BigDecimal.valueOf(1.5) : takerSweepBps.max(BigDecimal.ZERO);
        this.takerResultTimeoutMs = Math.max(100L, takerResultTimeoutMs);

        seedBalanceCache();
    }

    @Scheduled(fixedDelayString = "${maker.random-order.taker-fixed-delay-ms:200}")
    public void dispatchTakerTick() {
        if (!enabled || !takerEnabled) {
            return;
        }

        try {
            resolvePendingTakerOrders();
            emitTakerOrder(CONTRACTS.get(ThreadLocalRandom.current().nextInt(CONTRACTS.size())));
        } catch (Exception ex) {
            log.warn("taker scheduler skipped one tick: {}", ex.getMessage());
        }
    }

    private void emitTakerOrder(Contract contract) {
        BigDecimal reference = resolvePriceFromOrderBook(contract, anchors.get(contract));
        if (reference == null || reference.signum() <= 0) {
            return;
        }

        MarketClient.TopOfBookSnapshot top = marketClient.fetchTopOfBook(contract, 1);
        BigDecimal bestBid = top == null ? null : top.getBestBid();
        BigDecimal bestAsk = top == null ? null : top.getBestAsk();
        if ((bestBid == null || bestBid.signum() <= 0) && (bestAsk == null || bestAsk.signum() <= 0)) {
            return;
        }

        BigDecimal sideBias = evolveBias(contract);
        double buyProbability =
                0.5D + clamp(sideBias, BigDecimal.valueOf(-0.6), BigDecimal.valueOf(0.6)).doubleValue() / 2D;
        boolean buy = ThreadLocalRandom.current().nextDouble() < buyProbability;
        if (buy && (bestAsk == null || bestAsk.signum() <= 0)) {
            buy = false;
        } else if (!buy && (bestBid == null || bestBid.signum() <= 0)) {
            buy = true;
        }

        long userId = buy ? selectBestUserByAvailable(TAKER_USERS, contract.getQuote(), takerCursor, minAvailableQuote)
                : selectBestUserByAvailable(TAKER_USERS, contract.getBase(), takerCursor, minAvailableBase);
        if (userId < 0) {
            return;
        }
        BigDecimal available = fetchAvailableBalance(userId, buy ? contract.getQuote() : contract.getBase());
        if (available == null || available.signum() <= 0) {
            return;
        }

        BigDecimal sweepFactor = BigDecimal.valueOf(10000).add(buy ? takerSweepBps : takerSweepBps.negate())
                .divide(BigDecimal.valueOf(10000), 8, RoundingMode.HALF_UP);
        BigDecimal executablePrice = (buy ? bestAsk : bestBid).multiply(sweepFactor).setScale(4, RoundingMode.HALF_UP);
        if (executablePrice.signum() <= 0) {
            return;
        }

        BigDecimal takerNotional = randomTakerNotional();
        BigDecimal quantity;
        if (buy) {
            BigDecimal quoteBudget = available.max(BigDecimal.ZERO);
            quantity = takerNotional.divide(executablePrice, 6, RoundingMode.DOWN)
                    .min(quoteBudget.divide(executablePrice, 6, RoundingMode.DOWN));
        } else {
            quantity = takerNotional.divide(executablePrice, 6, RoundingMode.DOWN).min(available.max(BigDecimal.ZERO));
        }
        quantity = quantity.setScale(6, RoundingMode.DOWN);
        if (quantity.compareTo(MIN_QTY) < 0) {
            return;
        }

        OrderSubmitRequest order = buildOrder(userId, contract, buy ? Side.BUY : Side.SELL, executablePrice, quantity,
                OrderType.MARKET, TimeInForce.IOC);
        orderExecutionStateCache.registerSubmittedOrder(order, "TAKER");
        orderClient.submitOrder(order);
        pendingTakerOrders.put(order.getOrderId(), System.currentTimeMillis());
    }

    private void resolvePendingTakerOrders() {
        if (pendingTakerOrders.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, Long>> iterator = pendingTakerOrders.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            long orderId = entry.getKey().longValue();
            long submittedAt = entry.getValue().longValue();
            OrderExecutionStateCache.OrderExecutionState state = orderExecutionStateCache.get(orderId);
            if (state == null) {
                if (now - submittedAt >= takerResultTimeoutMs) {
                    iterator.remove();
                }
                continue;
            }

            boolean done = state.isTerminal() || now - submittedAt >= takerResultTimeoutMs;
            if (!done) {
                continue;
            }

            log.debug("taker order result. orderId={}, traded={}, matchedQty={}, execType={}, status={}", orderId,
                    state.hasAnyTrade(), state.getTotalMatchedQuantity(), state.getLastExecType(),
                    state.getOrderStatus());
            iterator.remove();
            orderExecutionStateCache.remove(orderId);
        }
    }

    private BigDecimal evolveBias(Contract contract) {
        BigDecimal current = sideBiasByContract.getOrDefault(contract, BigDecimal.ZERO);
        double shock = ThreadLocalRandom.current().nextGaussian() * 0.06D;
        BigDecimal drifted = current.multiply(new BigDecimal("0.9")).add(BigDecimal.valueOf(shock));
        BigDecimal next = clamp(drifted, BigDecimal.valueOf(-0.6), BigDecimal.valueOf(0.6));
        sideBiasByContract.put(contract, next);
        return next;
    }

    private BigDecimal randomTakerNotional() {
        int rand = ThreadLocalRandom.current().nextInt(70, 131);
        BigDecimal noise = BigDecimal.valueOf(rand).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal center =
                minTakerNotionalUsdt.add(maxTakerNotionalUsdt).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        return center.multiply(noise).setScale(4, RoundingMode.HALF_UP);
    }

    private OrderSubmitRequest buildOrder(long userId, Contract contract, Side side, BigDecimal price,
            BigDecimal quantity, OrderType orderType, TimeInForce timeInForce) {
        OrderSubmitRequest order = new OrderSubmitRequest();
        order.setOrderId(OrderIdGenerator.nextId());
        order.setUserId(userId);
        order.setContract(contract);
        order.setSide(side);
        order.setOrderType(orderType);
        order.setTimeInForce(timeInForce);
        order.setPrice(price);
        order.setTotalQuantity(quantity);
        order.setSource(Source.SCHEDULER);
        order.setAction(OrderAction.NEW);
        order.setTargetOrderId(0L);
        return order;
    }

    private long selectBestUserByAvailable(long[] users, Currency currency, AtomicInteger cursor,
            BigDecimal minThreshold) {
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
        return bestUser;
    }

    private BigDecimal resolvePriceFromOrderBook(Contract contract, BigDecimal fallbackAnchor) {
        BigDecimal cachedPrice = tickerCache.getLastPrice(contract);
        if (cachedPrice != null && cachedPrice.signum() > 0) {
            return cachedPrice.setScale(4, RoundingMode.HALF_UP);
        }
        return fallbackAnchor;
    }

    private BigDecimal fetchAvailableBalance(long userId, Currency currency) {
        return balanceCache.getAvailableBalance(userId, currency);
    }

    private void seedBalanceCache() {
        for (long taker : TAKER_USERS) {
            balanceCache.register(taker, Currency.USDT, BigDecimal.valueOf(2_000_000L));
            balanceCache.register(taker, Currency.BTC, BigDecimal.valueOf(200L));
            balanceCache.register(taker, Currency.ETH, BigDecimal.valueOf(2000L));
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
