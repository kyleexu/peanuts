package com.ganten.peanuts.market.messaging.subscriber;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.market.service.OrderBookAggregationService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.OrderBookCodec;
import com.ganten.peanuts.protocol.model.OrderBookProto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketOrderBookAeronSubscriber extends AbstractAeronSubscriber<OrderBookProto, OrderBookCodec> {

    private final OrderBookAggregationService orderBookAggregationService;

    public MarketOrderBookAeronSubscriber(@Qualifier("orderBookSubscriber") AeronProperties aeronProperties,
            OrderBookAggregationService orderBookAggregationService) {
        super(aeronProperties, OrderBookCodec.getInstance());
        this.orderBookAggregationService = orderBookAggregationService;
    }

    @Override
    protected void onMessage(OrderBookProto snapshot) {
        orderBookAggregationService.onOrderBook(snapshot);
    }
}
