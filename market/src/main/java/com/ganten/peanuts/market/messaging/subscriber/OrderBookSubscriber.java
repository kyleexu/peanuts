package com.ganten.peanuts.market.messaging.subscriber;

import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.entity.AeronProperties;
import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.common.enums.RaftApplyMode;
import com.ganten.peanuts.market.service.OrderBookService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.codec.OrderBookCodec;
import com.ganten.peanuts.protocol.model.OrderBookProto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderBookSubscriber extends AbstractAeronSubscriber<OrderBookProto, OrderBookCodec> {
    private static final String INSTANCE_TAG = "market";

    private final OrderBookService orderBookService;

    public OrderBookSubscriber(OrderBookService orderBookService) {
        super(buildProperties(), OrderBookCodec.getInstance());
        this.orderBookService = orderBookService;
    }

    private static AeronProperties buildProperties() {
        AeronProperties properties = AeronStream.ORDER_BOOK.toProperties(INSTANCE_TAG);
        properties.setEnableRaft(false);
        properties.setRaftApplyMode(RaftApplyMode.DISABLE);
        return properties;
    }

    /**
     * 第 11 步，消费订单簿快照，并聚合订单簿
     * 关键: 会调用 orderBookService 进行订单簿聚合
     */
    @Override
    protected void onMessage(OrderBookProto snapshot) {
        orderBookService.onOrderBook(snapshot);
    }
}
