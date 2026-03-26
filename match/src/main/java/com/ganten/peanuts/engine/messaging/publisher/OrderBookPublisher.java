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

    @Override
    protected void onPublicationUnavailable(OrderBookProto message) {
        log.warn("OrderBook publication unavailable. streamId={}", properties.getStreamId());
    }

    @Override
    protected void onOfferResult(OrderBookProto message, long result) {
        if (result < 0) {
            log.warn("OrderBook offer failed. streamId={}, result={}, contract={}, bidSize={}, askSize={}",
                    properties.getStreamId(), result, message == null ? null : message.getContract(),
                    message == null || message.getBidOrders() == null ? -1 : message.getBidOrders().size(),
                    message == null || message.getAskOrders() == null ? -1 : message.getAskOrders().size());
        }
    }

    @Override
    protected void onPublishError(OrderBookProto message, Throwable error) {
        log.warn("OrderBook publish error. streamId={}, contract={}, error={}",
                properties.getStreamId(), message == null ? null : message.getContract(), error.getMessage(), error);
    }
}
