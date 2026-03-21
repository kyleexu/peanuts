package com.ganten.peanuts.gateway.scheduler;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.*;
import com.ganten.peanuts.gateway.service.OrderService;

@Component
public class RandomOrderScheduler {

    private final OrderService orderService;
    private final boolean enabled;

    public RandomOrderScheduler(OrderService orderService,
            @Value("${gateway.random-order.enabled:true}") boolean enabled) {
        this.orderService = orderService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${gateway.random-order.fixed-delay-ms:1000}")
    public void dispatchRandomOrder() {
        if (!enabled) {
            return;
        }

        Contract contract = ThreadLocalRandom.current().nextBoolean() ? Contract.BTC_USDT : Contract.ETH_USDT;
        Side side = ThreadLocalRandom.current().nextBoolean() ? Side.BUY : Side.SELL;
        int quantity = ThreadLocalRandom.current().nextInt(1, 6);
        int price = ThreadLocalRandom.current().nextInt(20000, 80001);

        Order order = new Order();
        order.setOrderId(System.nanoTime());
        order.setUserId(10001L);
        order.setContract(contract);
        order.setSide(side);
        order.setOrderType(OrderType.LIMIT);
        order.setTimeInForce(TimeInForce.GTC);
        order.setPrice(BigDecimal.valueOf(price));
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setTotalQuantity(BigDecimal.valueOf(quantity));
        order.setTimestamp(System.currentTimeMillis());
        order.setSource(Source.SCHEDULER);

        orderService.submitOrder(order);
    }
}
