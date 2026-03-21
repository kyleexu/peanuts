package com.ganten.peanuts.sync.raft;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.OrderCodec;
import com.ganten.peanuts.protocol.model.OrderProto;

import lombok.extern.slf4j.Slf4j;

/**
 * order → match 订单入站（stream 2001）。
 */
@Slf4j
@Component
public class RaftOrderCommandLogSubscriber extends AbstractAeronSubscriber<OrderProto, OrderCodec> {

    public RaftOrderCommandLogSubscriber(
            @Qualifier("syncOrderCommandAeronProperties") AeronProperties aeronProperties) {
        super(aeronProperties, OrderCodec.getInstance());
    }

    @Override
    protected void onMessage(OrderProto message) {
        log.info("[raft] role=order stream={} entry=OrderCommand orderId={} userId={} contract={} side={} "
                + "type={} tif={} price={} qty={} ts={} action={} targetOrderId={}",
                Constants.AERON_STREAM_ID_ORDER,
                message.getOrderId(),
                message.getUserId(),
                message.getContract(),
                message.getSide(),
                message.getOrderType(),
                message.getTimeInForce(),
                message.getPrice(),
                message.getTotalQuantity(),
                message.getTimestamp(),
                message.getAction(),
                message.getTargetOrderId());
    }
}
