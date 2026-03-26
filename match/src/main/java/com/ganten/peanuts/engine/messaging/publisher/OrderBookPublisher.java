package com.ganten.peanuts.engine.messaging.publisher;

import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.protocol.codec.OrderBookCodec;
import com.ganten.peanuts.protocol.model.OrderBookProto;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * 订单簿 Aeron 发布者，用于推送订单簿快照到 Aeron channel
 */
@Slf4j
@Component
public class OrderBookPublisher extends AbstractAeronPublisher<OrderBookProto, OrderBookCodec> {

    public OrderBookPublisher() {
        super(AeronStream.ORDER_BOOK.toProperties(), OrderBookCodec.getInstance());
    }
}
