package com.ganten.peanuts.engine.messaging.subscriber;

import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.engine.mapping.ProtocolModelMapper;
import com.ganten.peanuts.engine.messaging.publisher.OrderBookPublisher;
import com.ganten.peanuts.engine.model.OrderBook;
import com.ganten.peanuts.engine.service.MatchService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.codec.OrderCodec;
import com.ganten.peanuts.protocol.model.OrderBookProto;
import com.ganten.peanuts.protocol.model.OrderProto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderSubscriber extends AbstractAeronSubscriber<OrderProto, OrderCodec> {

    private final OrderBookPublisher orderBookPublisher;
    private final MatchService matchService;

    public OrderSubscriber(OrderBookPublisher orderBookPublisher,
            MatchService matchService) {
        super(AeronStream.ORDER.toProperties(), OrderCodec.getInstance());
        this.orderBookPublisher = orderBookPublisher;
        this.matchService = matchService;
    }

    /**
     * 第 9 步，消费订单，将订单推送到撮合引擎进行撮合
     * 关键: 撮合完成之后，发送订单簿快照、交易、执行报告
     */
    @Override
    protected void onMessage(OrderProto command) {
        Order order = ProtocolModelMapper.toDomainOrder(command);
        log.info("Order received, orderId={}, userId={}", order.getOrderId(), order.getUserId());
        matchService.match(order);
        this.publishOrderBook(order.getContract());
    }

    /**
     * 第 11 步，推送订单簿快照
     * 关键: 只要是有订单进入撮合引擎，就需要推送订单簿快照
     */
    private void publishOrderBook(Contract contract) {
        try {
            OrderBook orderBook = matchService.getOrderBook(contract);
            OrderBookProto snapshot = ProtocolModelMapper.toRawOrderBookSnapshot(contract, orderBook);
            orderBookPublisher.offer(snapshot);
            log.debug("Order book published for contract={}", contract);
        } catch (Exception e) {
            log.warn("Failed to publish order book for contract={}: {}", contract, e.getMessage());
        }
    }
}
