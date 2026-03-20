package com.ganten.peanuts.engine.messaging.subscriber;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.ExecType;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.engine.config.MatchEngineProperties;
import com.ganten.peanuts.engine.mapping.ProtocolModelMapper;
import com.ganten.peanuts.engine.messaging.publisher.AeronExecutionReportPublisher;
import com.ganten.peanuts.engine.messaging.publisher.AeronOrderBookPublisher;
import com.ganten.peanuts.engine.messaging.publisher.AeronTradePublisher;
import com.ganten.peanuts.engine.model.OrderBook;
import com.ganten.peanuts.engine.service.MatchService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.codec.OrderCodec;
import org.agrona.DirectBuffer;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;
import com.ganten.peanuts.protocol.model.OrderBookSnapshotProto;
import com.ganten.peanuts.protocol.model.OrderProto;
import com.ganten.peanuts.protocol.model.TradeProto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AeronOrderSubscriber extends AbstractAeronSubscriber<OrderProto> {

    private final MatchEngineProperties properties;
    private final AeronExecutionReportPublisher publisher;
    private final AeronTradePublisher tradePublisher;
    private final AeronOrderBookPublisher orderBookPublisher;
    private final MatchService matchService;

    private final AtomicLong tradeIdGenerator = new AtomicLong(System.currentTimeMillis() * 1_000L);

    public AeronOrderSubscriber(MatchEngineProperties properties, AeronExecutionReportPublisher publisher,
            AeronTradePublisher tradePublisher, AeronOrderBookPublisher orderBookPublisher,
            MatchService matchService) {
        this.properties = properties;
        this.publisher = publisher;
        this.tradePublisher = tradePublisher;
        this.orderBookPublisher = orderBookPublisher;
        this.matchService = matchService;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        if (publisher.aeron() == null) {
            log.warn("Skip subscription init because Aeron connection is unavailable");
            return;
        }
        subscription = publisher.aeron().addSubscription(properties.getChannel(), properties.getInboundStreamId());
        start("match-engine-order-poller", subscription, properties.getFragmentLimit(),
                ex -> log.error("Order subscriber poll loop failed", ex));
        log.info("Order subscriber ready. channel={}, streamId={}", properties.getChannel(),
                properties.getInboundStreamId());
    }

    @PreDestroy
    public void shutdown() {
        super.shutdown();
    }

    @Override
    protected OrderProto decode(DirectBuffer buffer, int offset) {
        return OrderCodec.getInstance().decode(buffer, offset);
    }

    @Override
    protected void onMessage(OrderProto command) {
        Order order = ProtocolModelMapper.toDomainOrder(command);
        log.info("Order received, orderId={}, userId={}", order.getOrderId(), order.getUserId());
        List<ExecutionReportProto> reports = matchService.match(order);
        for (ExecutionReportProto report : reports) {
            publisher.publish(report);
        }
        publishTrades(reports);

        // 推送订单簿快照
        publishOrderBook(order.getContract());
    }

    private void publishTrades(List<ExecutionReportProto> reports) {
        for (int i = 0; i < reports.size() - 1; i++) {
            ExecutionReportProto first = reports.get(i);
            ExecutionReportProto second = reports.get(i + 1);

            ExecutionReportProto buyReport;
            ExecutionReportProto sellReport;
            if (first.getSide() == Side.BUY && second.getSide() == Side.SELL) {
                buyReport = first;
                sellReport = second;
            } else if (first.getSide() == Side.SELL && second.getSide() == Side.BUY) {
                buyReport = second;
                sellReport = first;
            } else {
                continue;
            }

            if (buyReport.getExecType() != ExecType.TRADE || sellReport.getExecType() != ExecType.TRADE) {
                continue;
            }
            if (buyReport.getOrderId() != sellReport.getCounterpartyOrderId()
                    || sellReport.getOrderId() != buyReport.getCounterpartyOrderId()) {
                continue;
            }
            if (buyReport.getMatchedQuantity() == null || buyReport.getMatchedQuantity().signum() <= 0) {
                continue;
            }

            Trade trade = new Trade();
            trade.setTradeId(tradeIdGenerator.incrementAndGet());
            trade.setBuyOrderId(buyReport.getOrderId());
            trade.setSellOrderId(sellReport.getOrderId());
            trade.setBuyUserId(buyReport.getUserId());
            trade.setSellUserId(sellReport.getUserId());
            trade.setContract(buyReport.getContract());
            trade.setPrice(buyReport.getMatchedPrice());
            trade.setQuantity(buyReport.getMatchedQuantity());
            trade.setTimestamp(Math.max(buyReport.getTimestamp(), sellReport.getTimestamp()));
            TradeProto event = ProtocolModelMapper.toTradeEvent(trade);
            tradePublisher.publish(event);
            i++;
        }
    }

    /**
     * 推送订单簿快照到 Aeron channel
     *
     * @param contract 合约
     */
    private void publishOrderBook(Contract contract) {
        try {
            OrderBook orderBook = matchService.getOrderBook(contract);
            OrderBookSnapshotProto snapshot = ProtocolModelMapper.toRawOrderBookSnapshot(contract, orderBook);
            orderBookPublisher.publish(snapshot);
            log.debug("Order book published for contract={}", contract);
        } catch (Exception e) {
            log.warn("Failed to publish order book for contract={}: {}", contract, e.getMessage());
        }
    }
}
