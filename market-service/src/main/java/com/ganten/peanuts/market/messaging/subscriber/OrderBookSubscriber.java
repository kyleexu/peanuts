package com.ganten.peanuts.market.messaging.subscriber;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.market.service.OrderBookService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.OrderBookCodec;
import com.ganten.peanuts.protocol.model.OrderBookProto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderBookSubscriber extends AbstractAeronSubscriber<OrderBookProto, OrderBookCodec> {

    private final OrderBookService orderBookAggregationService;

    public OrderBookSubscriber(@Qualifier("orderBookSubscriber") AeronProperties aeronProperties,
            OrderBookService orderBookAggregationService) {
        super(aeronProperties, OrderBookCodec.getInstance());
        this.orderBookAggregationService = orderBookAggregationService;
    }

    /**
     * 第 11 步，消费订单簿快照，并聚合订单簿
     * 关键: 会调用 orderBookAggregationService 进行订单簿聚合
     */
    @Override
    protected void onMessage(OrderBookProto snapshot) {
        orderBookAggregationService.onOrderBook(snapshot);
    }
}
