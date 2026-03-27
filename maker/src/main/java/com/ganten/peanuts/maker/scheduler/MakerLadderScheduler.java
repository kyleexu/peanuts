package com.ganten.peanuts.maker.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.enums.*;
import com.ganten.peanuts.common.enums.Currency;
import com.ganten.peanuts.maker.cache.BalanceCache;
import com.ganten.peanuts.maker.cache.OrderExecutionStateCache;
import com.ganten.peanuts.maker.cache.TickerCache;
import com.ganten.peanuts.maker.client.OrderClient;
import com.ganten.peanuts.maker.entity.LadderOrderRef;
import com.ganten.peanuts.maker.model.OrderSubmitRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MakerLadderScheduler {

    private static final BigDecimal MIN_QTY = new BigDecimal("0.000001");
    private static final BigDecimal CANCEL_DUMMY = new BigDecimal("0.00000001");
    private static final long[] MAKER_USERS = {10001L, 10002L, 10003L};
    private static final List<Contract> CONTRACTS = Arrays.asList(Contract.BTC_USDT);

    private final boolean enabled;
    private final BalanceCache balanceCache;
    private final OrderClient orderClient;
    private final OrderExecutionStateCache orderExecutionStateCache;
    private final TickerCache tickerCache;
    private final BigDecimal minAvailableQuote;
    private final BigDecimal minAvailableBase;
    private final int ladderLevels;
    private final BigDecimal ladderStepBps;
    private final BigDecimal minLadderNotionalUsdt;
    private final BigDecimal maxLadderNotionalUsdt;
    private final int maxActiveOrdersPerContract;
    private final BigDecimal rebalanceDeviationBps;
    private final long rebalanceIntervalMs;
    private final BigDecimal inventoryTargetBaseQty;
    private final BigDecimal inventorySoftLimitBaseQty;
    private final BigDecimal inventoryBiasCap;

    private final Map<Contract, BigDecimal> anchors = new ConcurrentHashMap<Contract, BigDecimal>();
    private final Map<Contract, BigDecimal> sideBiasByContract = new ConcurrentHashMap<Contract, BigDecimal>();
    private final Map<Contract, BigDecimal> ladderMidByContract = new ConcurrentHashMap<Contract, BigDecimal>();
    private final Map<Contract, Long> ladderRefreshAtByContract = new ConcurrentHashMap<Contract, Long>();
    private final Map<Contract, List<LadderOrderRef>> liveLadderOrders =
            new ConcurrentHashMap<Contract, List<LadderOrderRef>>();
    private final AtomicInteger makerCursor = new AtomicInteger(0);

    public MakerLadderScheduler(BalanceCache balanceCache, OrderClient orderClient,
            OrderExecutionStateCache orderExecutionStateCache, TickerCache tickerCache,
            @Value("${maker.random-order.enabled:true}") boolean enabled,
            @Value("${maker.random-order.min-available-quote:1000}") BigDecimal minAvailableQuote,
            @Value("${maker.random-order.min-available-base:0.5}") BigDecimal minAvailableBase,
            @Value("${maker.random-order.ladder-levels:6}") int ladderLevels,
            @Value("${maker.random-order.ladder-step-bps:8}") BigDecimal ladderStepBps,
            @Value("${maker.random-order.min-ladder-notional-usdt:120}") BigDecimal minLadderNotionalUsdt,
            @Value("${maker.random-order.max-ladder-notional-usdt:400}") BigDecimal maxLadderNotionalUsdt,
            @Value("${maker.random-order.max-active-orders-per-contract:12}") int maxActiveOrdersPerContract,
            @Value("${maker.random-order.rebalance-deviation-bps:25}") BigDecimal rebalanceDeviationBps,
            @Value("${maker.random-order.rebalance-interval-ms:15000}") long rebalanceIntervalMs,
            @Value("${maker.random-order.inventory-target-base-qty:0}") BigDecimal inventoryTargetBaseQty,
            @Value("${maker.random-order.inventory-soft-limit-base-qty:30}") BigDecimal inventorySoftLimitBaseQty,
            @Value("${maker.random-order.inventory-bias-cap:0.5}") BigDecimal inventoryBiasCap) {
        this.balanceCache = balanceCache;
        this.orderClient = orderClient;
        this.orderExecutionStateCache = orderExecutionStateCache;
        this.tickerCache = tickerCache;
        this.enabled = enabled;
        this.minAvailableQuote = minAvailableQuote;
        this.minAvailableBase = minAvailableBase;
        this.ladderLevels = Math.max(2, ladderLevels);
        this.ladderStepBps = ladderStepBps == null ? BigDecimal.valueOf(8) : ladderStepBps.max(BigDecimal.ONE);
        this.minLadderNotionalUsdt =
                minLadderNotionalUsdt == null ? BigDecimal.valueOf(120) : minLadderNotionalUsdt.max(BigDecimal.TEN);
        this.maxLadderNotionalUsdt = maxLadderNotionalUsdt == null ? BigDecimal.valueOf(400)
                : maxLadderNotionalUsdt.max(this.minLadderNotionalUsdt);
        this.maxActiveOrdersPerContract = Math.max(2, maxActiveOrdersPerContract);
        this.rebalanceDeviationBps =
                rebalanceDeviationBps == null ? BigDecimal.valueOf(25) : rebalanceDeviationBps.max(BigDecimal.ONE);
        this.rebalanceIntervalMs = Math.max(1000L, rebalanceIntervalMs);
        this.inventoryTargetBaseQty = inventoryTargetBaseQty == null ? BigDecimal.ZERO : inventoryTargetBaseQty;
        this.inventorySoftLimitBaseQty = inventorySoftLimitBaseQty == null ? BigDecimal.valueOf(30)
                : inventorySoftLimitBaseQty.max(BigDecimal.ONE);
        this.inventoryBiasCap = inventoryBiasCap == null ? BigDecimal.valueOf(0.5)
                : clamp(inventoryBiasCap.abs(), BigDecimal.valueOf(0.05), BigDecimal.valueOf(0.95));

        anchors.put(Contract.BTC_USDT, BigDecimal.valueOf(45000));
        anchors.put(Contract.ETH_USDT, BigDecimal.valueOf(3000));
        seedBalanceCache();
    }

    @Scheduled(fixedDelayString = "${maker.random-order.fixed-delay-ms:1000}")
    public void dispatchMakerTick() {
        if (!enabled) {
            return;
        }

        try {
            Contract contract = CONTRACTS.get(ThreadLocalRandom.current().nextInt(CONTRACTS.size()));
            BigDecimal anchor = moveAnchor(contract);
            emitLadderOrders(contract, anchor);
        } catch (Exception ex) {
            log.warn("maker scheduler skipped one tick: {}", ex.getMessage());
        }
    }

    private BigDecimal moveAnchor(Contract contract) {
        BigDecimal current = anchors.get(contract);
        double drift = ThreadLocalRandom.current().nextGaussian() * 0.0005D;
        BigDecimal factor = BigDecimal.valueOf(1D + drift);
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
        long buyUser = selectBestUserByAvailable(MAKER_USERS, contract.getQuote(), makerCursor, minAvailableQuote);
        long sellUser = selectBestUserByAvailable(MAKER_USERS, contract.getBase(), makerCursor, minAvailableBase);
        BigDecimal price = resolvePriceFromOrderBook(contract, anchor);
        if (price == null || price.signum() <= 0) {
            return;
        }

        BigDecimal buyQuoteAvailable = fetchAvailableBalance(buyUser, contract.getQuote());
        BigDecimal sellBaseAvailable = fetchAvailableBalance(sellUser, contract.getBase());
        if (buyQuoteAvailable == null || sellBaseAvailable == null) {
            return;
        }

        maybeRebalanceLadder(contract, price);
        List<LadderOrderRef> currentRefs =
                liveLadderOrders.computeIfAbsent(contract, k -> new ArrayList<LadderOrderRef>());
        pruneCompletedLadderOrders(currentRefs);
        int missingOrders = Math.max(0, maxActiveOrdersPerContract - currentRefs.size());
        if (missingOrders <= 0) {
            return;
        }

        BigDecimal sideBias = combineBias(contract, price);
        List<OrderSubmitRequest> orders = buildLadderOrders(contract, buyUser, sellUser, price, buyQuoteAvailable,
                sellBaseAvailable, missingOrders, sideBias);
        for (OrderSubmitRequest order : orders) {
            orderExecutionStateCache.registerSubmittedOrder(order, "MAKER");
            submitOrder(order);
            currentRefs.add(new LadderOrderRef(order.getOrderId(), order.getUserId(), order.getContract()));
        }
        ladderMidByContract.put(contract, price);
        ladderRefreshAtByContract.put(contract, System.currentTimeMillis());
    }

    private List<OrderSubmitRequest> buildLadderOrders(Contract contract, long buyUser, long sellUser,
            BigDecimal midPrice, BigDecimal buyQuoteAvailable, BigDecimal sellBaseAvailable, int maxOrders,
            BigDecimal sideBias) {
        List<OrderSubmitRequest> orders = new ArrayList<OrderSubmitRequest>();
        BigDecimal totalBuyQtyBudget = buyQuoteAvailable.divide(midPrice, 6, RoundingMode.DOWN);
        BigDecimal remainingBuyQty = totalBuyQtyBudget.max(BigDecimal.ZERO);
        BigDecimal remainingSellQty = sellBaseAvailable.max(BigDecimal.ZERO);
        BigDecimal oneBps = BigDecimal.valueOf(10000);
        BigDecimal clampedBias =
                clamp(sideBias == null ? BigDecimal.ZERO : sideBias, BigDecimal.valueOf(-0.6), BigDecimal.valueOf(0.6));
        BigDecimal buyStrength = BigDecimal.ONE.add(clampedBias).max(new BigDecimal("0.2"));
        BigDecimal sellStrength = BigDecimal.ONE.subtract(clampedBias).max(new BigDecimal("0.2"));
        double buyProbability = 0.5D + clampedBias.doubleValue() / 2D;
        int maxAttempts = Math.max(maxOrders * 5, 8);

        for (int i = 0; i < maxAttempts && orders.size() < maxOrders; i++) {
            int level = ThreadLocalRandom.current().nextInt(1, ladderLevels + 1);
            boolean tryBuy = ThreadLocalRandom.current().nextDouble() < buyProbability;
            BigDecimal bpsOffset = ladderStepBps.multiply(BigDecimal.valueOf(level));
            BigDecimal buyFactor = oneBps.subtract(bpsOffset).divide(oneBps, 8, RoundingMode.HALF_UP);
            BigDecimal sellFactor = oneBps.add(bpsOffset).divide(oneBps, 8, RoundingMode.HALF_UP);
            if (buyFactor.signum() <= 0) {
                continue;
            }

            BigDecimal levelNotional = randomNotionalForLevel(level);
            if (tryBuy && remainingBuyQty.compareTo(MIN_QTY) >= 0) {
                BigDecimal buyPrice = midPrice.multiply(buyFactor).setScale(4, RoundingMode.HALF_UP);
                BigDecimal buyNotional = levelNotional.multiply(buyStrength);
                BigDecimal buyQty = buyNotional.divide(buyPrice, 6, RoundingMode.DOWN).min(remainingBuyQty)
                        .max(BigDecimal.ZERO).setScale(6, RoundingMode.DOWN);
                if (buyQty.compareTo(MIN_QTY) >= 0) {
                    orders.add(buildOrder(buyUser, contract, Side.BUY, buyPrice, buyQty, OrderType.LIMIT,
                            TimeInForce.GTC));
                    remainingBuyQty = remainingBuyQty.subtract(buyQty).max(BigDecimal.ZERO);
                }
                continue;
            }

            if (remainingSellQty.compareTo(MIN_QTY) >= 0) {
                BigDecimal sellPrice = midPrice.multiply(sellFactor).setScale(4, RoundingMode.HALF_UP);
                BigDecimal sellNotional = levelNotional.multiply(sellStrength);
                BigDecimal sellQty = sellNotional.divide(sellPrice, 6, RoundingMode.DOWN).min(remainingSellQty)
                        .max(BigDecimal.ZERO).setScale(6, RoundingMode.DOWN);
                if (sellQty.compareTo(MIN_QTY) >= 0) {
                    orders.add(buildOrder(sellUser, contract, Side.SELL, sellPrice, sellQty, OrderType.LIMIT,
                            TimeInForce.GTC));
                    remainingSellQty = remainingSellQty.subtract(sellQty).max(BigDecimal.ZERO);
                }
            }
        }
        return orders;
    }

    private void maybeRebalanceLadder(Contract contract, BigDecimal marketMidPrice) {
        BigDecimal lastMid = ladderMidByContract.get(contract);
        long now = System.currentTimeMillis();
        Long lastRefreshAt = ladderRefreshAtByContract.get(contract);
        boolean intervalExpired = lastRefreshAt == null || now - lastRefreshAt.longValue() >= rebalanceIntervalMs;
        boolean driftTooLarge = false;
        if (lastMid != null && lastMid.signum() > 0) {
            BigDecimal diff = marketMidPrice.subtract(lastMid).abs();
            BigDecimal diffBps = diff.multiply(BigDecimal.valueOf(10000)).divide(lastMid, 8, RoundingMode.HALF_UP);
            driftTooLarge = diffBps.compareTo(rebalanceDeviationBps) >= 0;
        }
        if (driftTooLarge || intervalExpired) {
            cancelExistingLadderOrders(contract);
            ladderMidByContract.remove(contract);
            ladderRefreshAtByContract.put(contract, now);
        }
    }

    private BigDecimal combineBias(Contract contract, BigDecimal midPrice) {
        BigDecimal stochastic = evolveStochasticBias(contract);
        BigDecimal inventory = inventoryBias(contract, midPrice);
        return clamp(stochastic.add(inventory), BigDecimal.valueOf(-0.6), BigDecimal.valueOf(0.6));
    }

    private BigDecimal evolveStochasticBias(Contract contract) {
        BigDecimal current = sideBiasByContract.getOrDefault(contract, BigDecimal.ZERO);
        double shock = ThreadLocalRandom.current().nextGaussian() * 0.06D;
        BigDecimal drifted = current.multiply(new BigDecimal("0.9")).add(BigDecimal.valueOf(shock));
        BigDecimal next = clamp(drifted, BigDecimal.valueOf(-0.6), BigDecimal.valueOf(0.6));
        sideBiasByContract.put(contract, next);
        return next;
    }

    private BigDecimal inventoryBias(Contract contract, BigDecimal midPrice) {
        BigDecimal aggregateBase = BigDecimal.ZERO;
        for (long userId : MAKER_USERS) {
            BigDecimal available = fetchAvailableBalance(userId, contract.getBase());
            if (available != null && available.signum() > 0) {
                aggregateBase = aggregateBase.add(available);
            }
        }
        BigDecimal delta = aggregateBase.subtract(inventoryTargetBaseQty);
        BigDecimal normalized = delta.divide(inventorySoftLimitBaseQty, 8, RoundingMode.HALF_UP);
        BigDecimal clipped = clamp(normalized, BigDecimal.valueOf(-1), BigDecimal.ONE);
        return clipped.negate().multiply(inventoryBiasCap).setScale(8, RoundingMode.HALF_UP);
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
                log.debug("Cancel old ladder order failed. contract={}, orderId={}, error={}", contract,
                        ref.getOrderId(), ex.getMessage());
            }
            orderExecutionStateCache.remove(ref.getOrderId());
        }
    }

    private void pruneCompletedLadderOrders(List<LadderOrderRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        Iterator<LadderOrderRef> iterator = refs.iterator();
        while (iterator.hasNext()) {
            LadderOrderRef ref = iterator.next();
            OrderExecutionStateCache.OrderExecutionState state = orderExecutionStateCache.get(ref.getOrderId());
            if (state == null) {
                continue;
            }
            if (state.isTerminal()) {
                iterator.remove();
                if (state.hasAnyTrade()) {
                    log.debug("maker order consumed. orderId={}, userId={}, matchedQty={}, status={}", ref.getOrderId(),
                            ref.getUserId(), state.getTotalMatchedQuantity(), state.getOrderStatus());
                }
                orderExecutionStateCache.remove(ref.getOrderId());
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
        orderClient.submitOrder(order);
    }

    private OrderSubmitRequest buildOrder(long userId, Contract contract, Side side, BigDecimal price,
            BigDecimal quantity, OrderType orderType, TimeInForce timeInForce) {
        OrderSubmitRequest order = new OrderSubmitRequest();
        order.setOrderId(System.nanoTime());
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
        long bestUser = users[start];
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
        for (long maker : MAKER_USERS) {
            balanceCache.register(maker, Currency.USDT, BigDecimal.valueOf(1_000_000L));
            balanceCache.register(maker, Currency.BTC, BigDecimal.valueOf(100L));
            balanceCache.register(maker, Currency.ETH, BigDecimal.valueOf(1000L));
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
