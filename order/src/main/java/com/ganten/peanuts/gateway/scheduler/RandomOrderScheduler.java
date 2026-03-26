package com.ganten.peanuts.gateway.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.*;
import com.ganten.peanuts.gateway.service.OrderService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RandomOrderScheduler {

    private final OrderService orderService;
    private final boolean enabled;
    private final Map<Contract, BigDecimal> anchors = new ConcurrentHashMap<Contract, BigDecimal>();

    private static final long[] MAKER_USERS = {10001L, 10002L, 10003L};
    private static final long[] TAKER_USERS = {20001L, 20002L};

    public RandomOrderScheduler(OrderService orderService,
            @Value("${gateway.random-order.enabled:true}") boolean enabled) {
        this.orderService = orderService;
        this.enabled = enabled;
        anchors.put(Contract.BTC_USDT, BigDecimal.valueOf(45000));
        anchors.put(Contract.ETH_USDT, BigDecimal.valueOf(3000));
    }

    @Scheduled(fixedDelayString = "${gateway.random-order.fixed-delay-ms:1000}")
    public void dispatchRandomOrder() {
        if (!enabled) {
            return;
        }

        try {
            Contract contract = ThreadLocalRandom.current().nextBoolean() ? Contract.BTC_USDT : Contract.ETH_USDT;
            BigDecimal anchor = moveAnchor(contract);
            emitMakerQuotes(contract, anchor);
            maybeEmitTaker(contract, anchor);
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

    private void emitMakerQuotes(Contract contract, BigDecimal anchor) {
        long makerUser = pick(MAKER_USERS);
        BigDecimal spreadBps = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(6, 16)) // 0.06% - 0.15%
                .divide(BigDecimal.valueOf(10000));
        BigDecimal halfSpread = anchor.multiply(spreadBps);
        BigDecimal bid = anchor.subtract(halfSpread).max(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
        BigDecimal ask = anchor.add(halfSpread).max(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);

        BigDecimal makerQty = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(1, 4));
        submitOrder(buildOrder(makerUser, contract, Side.BUY, bid, makerQty));
        submitOrder(buildOrder(makerUser, contract, Side.SELL, ask, makerQty));
    }

    private void maybeEmitTaker(Contract contract, BigDecimal anchor) {
        // 70% probability to send a market-taking order that crosses maker quotes.
        if (ThreadLocalRandom.current().nextInt(10) >= 7) {
            return;
        }
        long takerUser = pick(TAKER_USERS);
        Side side = ThreadLocalRandom.current().nextBoolean() ? Side.BUY : Side.SELL;
        BigDecimal aggressiveBps = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(20, 61))
                .divide(BigDecimal.valueOf(10000)); // 0.20% - 0.60%

        BigDecimal price = side == Side.BUY
                ? anchor.multiply(BigDecimal.ONE.add(aggressiveBps))
                : anchor.multiply(BigDecimal.ONE.subtract(aggressiveBps));
        price = price.max(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
        BigDecimal qty = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(1, 6));
        submitOrder(buildOrder(takerUser, contract, side, price, qty));
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
