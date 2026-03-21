package com.ganten.peanuts.sync.raft;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.sync.kafka.RaftLogDelivery;
import com.ganten.peanuts.sync.kafka.RaftLogKafkaBridge;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.OrderBookCodec;
import com.ganten.peanuts.protocol.model.OrderBookProto;

import lombok.extern.slf4j.Slf4j;

/**
 * match → market 订单簿快照（stream 2004）。
 */
@Slf4j
@Component
public class RaftOrderBookLogSubscriber extends AbstractAeronSubscriber<OrderBookProto, OrderBookCodec> {

    private final ObjectProvider<RaftLogKafkaBridge> kafkaBridge;

    public RaftOrderBookLogSubscriber(
            @Qualifier("syncOrderBookAeronProperties") AeronProperties aeronProperties,
            ObjectProvider<RaftLogKafkaBridge> kafkaBridge) {
        super(aeronProperties, OrderBookCodec.getInstance());
        this.kafkaBridge = kafkaBridge;
    }

    @Override
    protected void onMessage(OrderBookProto message) {
        log.info("[raft] role=match stream={} entry=OrderBook {}",
                Constants.AERON_STREAM_ID_ORDER_BOOK,
                message);
        RaftLogDelivery.maybePublish(kafkaBridge, "match", Constants.AERON_STREAM_ID_ORDER_BOOK, "OrderBook", message);
    }
}
