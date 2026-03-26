package com.ganten.peanuts.gateway.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.*;
import com.ganten.peanuts.gateway.service.OrderService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RandomOrderScheduler {

    private final OrderService orderService;
    private final boolean enabled;
    private final String accountApiBaseUrl;
    private final String marketApiBaseUrl;
    private final int orderBookLevel;
    private final BigDecimal minAvailableQuote;
    private final BigDecimal minAvailableBase;
    private final int ladderLevels;
    private final BigDecimal ladderStepBps;
    private final BigDecimal minLadderNotionalUsdt;
    private final BigDecimal maxLadderNotionalUsdt;
    private final int overlapLevels;
    private final BigDecimal overlapBps;
    private final Map<Contract, BigDecimal> anchors = new ConcurrentHashMap<Contract, BigDecimal>();
    private final Map<String, BigDecimal> balanceCache = new ConcurrentHashMap<String, BigDecimal>();
    private final Map<Contract, List<LadderOrderRef>> liveLadderOrders = new ConcurrentHashMap<Contract, List<LadderOrderRef>>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final BigDecimal MIN_QTY = new BigDecimal("0.000001");

    private static final long[] MAKER_USERS = {10001L, 10002L, 10003L};
    private static final long[] TAKER_USERS = {20001L, 20002L};
    private final AtomicInteger makerCursor = new AtomicInteger(0);
    private final AtomicInteger takerCursor = new AtomicInteger(0);

    public RandomOrderScheduler(OrderService orderService,
            @Value("${gateway.random-order.enabled:true}") boolean enabled,
            @Value("${gateway.random-order.account-api-base-url:http://localhost:8081}") String accountApiBaseUrl,
            @Value("${gateway.random-order.market-api-base-url:http://localhost:8082}") String marketApiBaseUrl,
            @Value("${gateway.random-order.orderbook-level:1}") int orderBookLevel,
            @Value("${gateway.random-order.min-available-quote:1000}") BigDecimal minAvailableQuote,
            @Value("${gateway.random-order.min-available-base:0.5}") BigDecimal minAvailableBase,
            @Value("${gateway.random-order.ladder-levels:6}") int ladderLevels,
            @Value("${gateway.random-order.ladder-step-bps:8}") BigDecimal ladderStepBps,
            @Value("${gateway.random-order.min-ladder-notional-usdt:120}") BigDecimal minLadderNotionalUsdt,
            @Value("${gateway.random-order.max-ladder-notional-usdt:400}") BigDecimal maxLadderNotionalUsdt,
            @Value("${gateway.random-order.overlap-levels:2}") int overlapLevels,
            @Value("${gateway.random-order.overlap-bps:2}") BigDecimal overlapBps) {
        this.orderService = orderService;
        this.enabled = enabled;
        this.accountApiBaseUrl = accountApiBaseUrl;
        this.marketApiBaseUrl = marketApiBaseUrl;
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
        anchors.put(Contract.BTC_USDT, BigDecimal.valueOf(45000));
        anchors.put(Contract.ETH_USDT, BigDecimal.valueOf(3000));
        seedBalanceCache();
    }

    @Scheduled(fixedDelayString = "${gateway.random-order.fixed-delay-ms:1000}")
    public void dispatchRandomOrder() {
        if (!enabled) {
            return;
        }

        try {
            Contract contract = ThreadLocalRandom.current().nextBoolean() ? Contract.BTC_USDT : Contract.ETH_USDT;
            BigDecimal anchor = moveAnchor(contract);
            emitLadderOrders(contract, anchor);
        } catch (Exception ex) {
            // Keep scheduler alive even when one order fails.
            log.warn("random-order scheduler skipped one tick: {}", ex.getMessage());
        }
    }

    private BigDecimal moveAnchor(Contract contract) {
        BigDecimal current = anchors.get(contract);
        int bps = ThreadLocalRandom.current().nextInt(-8, 9); // +/-0.08%
        BigDecimal factor = BigDecimal.valueOf(10000 + bps).divide(BigDecimal.valueOf(10000));
        BigDecimal moved = current.multiply(factor);

        // Prevent long-term drift outside reasonable sandbox range.
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
            log.warn("Skip scheduler tick, invalid price. contract={}", contract);
            return;
        }

        BigDecimal buyQuoteAvailable = fetchAvailableBalance(buyUser, contract.getQuote());
        BigDecimal sellBaseAvailable = fetchAvailableBalance(sellUser, contract.getBase());
        if (buyQuoteAvailable == null || sellBaseAvailable == null) {
            log.warn("Skip scheduler tick, failed to fetch balances. contract={}, buyUser={}, sellUser={}",
                    contract, buyUser, sellUser);
            return;
        }

        cancelExistingLadderOrders(contract);
        List<Order> orders = buildLadderOrders(contract, buyUser, sellUser, price, buyQuoteAvailable, sellBaseAvailable);
        if (orders.isEmpty()) {
            log.info("Skip scheduler tick, no valid ladder orders. contract={}, buyUser={}, sellUser={}, buyQuoteAvailable={}, sellBaseAvailable={}, price={}",
                    contract, buyUser, sellUser, buyQuoteAvailable, sellBaseAvailable, price);
            return;
        }

        List<LadderOrderRef> refs = new ArrayList<LadderOrderRef>(orders.size());
        for (Order order : orders) {
            submitOrder(order);
            refs.add(new LadderOrderRef(order.getOrderId(), order.getUserId(), order.getContract()));
        }
        liveLadderOrders.put(contract, refs);
    }

    private List<Order> buildLadderOrders(Contract contract,
            long buyUser,
            long sellUser,
            BigDecimal midPrice,
            BigDecimal buyQuoteAvailable,
            BigDecimal sellBaseAvailable) {
        List<Order> orders = new ArrayList<Order>();
        BigDecimal totalBuyQtyBudget = buyQuoteAvailable.divide(midPrice, 6, RoundingMode.DOWN);
        BigDecimal remainingBuyQty = totalBuyQtyBudget.max(BigDecimal.ZERO);
        BigDecimal remainingSellQty = sellBaseAvailable.max(BigDecimal.ZERO);
        BigDecimal oneBps = BigDecimal.valueOf(10000);

        for (int level = 1; level <= ladderLevels; level++) {
            BigDecimal bpsOffset = ladderStepBps.multiply(BigDecimal.valueOf(level));
            BigDecimal buyFactor = oneBps.subtract(bpsOffset).divide(oneBps, 8, RoundingMode.HALF_UP);
            BigDecimal sellFactor = oneBps.add(bpsOffset).divide(oneBps, 8, RoundingMode.HALF_UP);
            if (level <= overlapLevels && overlapBps.signum() > 0) {
                // Inner levels intentionally cross to create continuous trades.
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
        int rand = ThreadLocalRandom.current().nextInt(70, 131); // 70% ~ 130%
        BigDecimal noise = BigDecimal.valueOf(rand).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal center = BigDecimal.valueOf(ladderLevels + 1).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal distance = BigDecimal.valueOf(level).subtract(center).abs();
        BigDecimal normalized = distance.divide(center, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        BigDecimal curve = BigDecimal.ONE.subtract(normalized.multiply(new BigDecimal("0.65"))); // center > edge
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
                        contract, ref.orderId, ex.getMessage());
            }
        }
    }

    private Order buildCancelOrder(LadderOrderRef ref) {
        Order cancel = new Order();
        cancel.setOrderId(System.nanoTime());
        cancel.setUserId(ref.userId);
        cancel.setContract(ref.contract);
        cancel.setSide(Side.BUY);
        cancel.setOrderType(OrderType.LIMIT);
        cancel.setTimeInForce(TimeInForce.GTC);
        cancel.setPrice(BigDecimal.ZERO);
        cancel.setFilledQuantity(BigDecimal.ZERO);
        cancel.setTotalQuantity(BigDecimal.ZERO);
        cancel.setTimestamp(System.currentTimeMillis());
        cancel.setSource(Source.SCHEDULER);
        cancel.setAction(OrderAction.CANCEL);
        cancel.setTargetOrderId(ref.orderId);
        return cancel;
    }

    private void submitOrder(Order order) {
        orderService.submitOrder(order);
    }

    private Order buildOrder(long userId, Contract contract, Side side, BigDecimal price, BigDecimal quantity) {
        Order order = new Order();
        order.setOrderId(System.nanoTime());
        order.setUserId(userId);
        order.setContract(contract);
        order.setSide(side);
        order.setOrderType(OrderType.LIMIT);
        order.setTimeInForce(TimeInForce.GTC);
        order.setPrice(price);
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setTotalQuantity(quantity);
        order.setTimestamp(System.currentTimeMillis());
        order.setSource(Source.SCHEDULER);
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
        try {
            String url = String.format("%s/api/market/orderbook/%s?level=%d",
                    this.marketApiBaseUrl, contract.name(), this.orderBookLevel);
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return fallbackAnchor;
            }
            JsonNode root = objectMapper.readTree(body);
            BigDecimal bid = firstPrice(root.path("bids"));
            BigDecimal ask = firstPrice(root.path("asks"));
            if (bid != null && ask != null && bid.signum() > 0 && ask.signum() > 0) {
                return bid.add(ask).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            }
            if (ask != null && ask.signum() > 0) {
                return ask.setScale(4, RoundingMode.HALF_UP);
            }
            if (bid != null && bid.signum() > 0) {
                return bid.setScale(4, RoundingMode.HALF_UP);
            }
        } catch (Exception ex) {
            log.debug("Read orderbook failed, use anchor fallback. contract={}, error={}", contract, ex.getMessage());
        }
        return fallbackAnchor;
    }

    private BigDecimal fetchAvailableBalance(long userId, Currency currency) {
        String key = cacheKey(userId, currency);
        try {
            String url = String.format("%s/api/accounts/%d/currencies/%s", this.accountApiBaseUrl.trim(), userId,
                    currency.name());
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return balanceCache.get(key);
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode available = root.path("available");
            if (available.isMissingNode() || available.isNull()) {
                return balanceCache.get(key);
            }
            BigDecimal value = new BigDecimal(available.asText("0"));
            balanceCache.put(key, value);
            return value;
        } catch (Exception ex) {
            BigDecimal cached = balanceCache.get(key);
            log.warn("Read account balance failed, fallback cache. userId={}, currency={}, baseUrl={}, cached={}, error={}",
                    userId, currency, this.accountApiBaseUrl, cached, ex.getMessage());
            return cached;
        }
    }

    private BigDecimal firstPrice(JsonNode levels) {
        if (levels == null || !levels.isArray() || levels.size() == 0) {
            return null;
        }
        JsonNode first = levels.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        JsonNode priceNode = first.path("price");
        if (priceNode.isMissingNode() || priceNode.isNull()) {
            return null;
        }
        return new BigDecimal(priceNode.asText("0"));
    }

    private String cacheKey(long userId, Currency currency) {
        return userId + ":" + currency.name();
    }

    private void seedBalanceCache() {
        // bootstrap with initial-balances defaults, then gradually corrected by live API values.
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

    private static final class LadderOrderRef {
        private final long orderId;
        private final long userId;
        private final Contract contract;

        private LadderOrderRef(long orderId, long userId, Contract contract) {
            this.orderId = orderId;
            this.userId = userId;
            this.contract = contract;
        }
    }
}
