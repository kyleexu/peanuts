package com.ganten.peanuts.engine.messaging.subscriber;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.ExecType;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.engine.mapping.ProtocolModelMapper;
import com.ganten.peanuts.engine.messaging.publisher.ExecutionReportPublisher;
import com.ganten.peanuts.engine.messaging.publisher.OrderBookPublisher;
import com.ganten.peanuts.engine.messaging.publisher.TradePublisher;
import com.ganten.peanuts.engine.model.OrderBook;
import com.ganten.peanuts.engine.service.MatchService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.codec.OrderCodec;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;
import com.ganten.peanuts.protocol.model.OrderBookProto;
import com.ganten.peanuts.protocol.model.OrderProto;
import com.ganten.peanuts.protocol.model.TradeProto;
import lombok.extern.slf4j.Slf4j;
import com.ganten.peanuts.protocol.aeron.AeronProperties;

@Slf4j
@Component
public class OrderSubscriber extends AbstractAeronSubscriber<OrderProto, OrderCodec> {

    private final ExecutionReportPublisher executionReportPublisher;
    private final TradePublisher tradePublisher;
    private final OrderBookPublisher orderBookPublisher;
    private final MatchService matchService;


    private final AtomicLong tradeIdGenerator = new AtomicLong(System.currentTimeMillis() * 1_000L);

    public OrderSubscriber(AeronProperties properties, ExecutionReportPublisher executionReportPublisher,
            TradePublisher tradePublisher, OrderBookPublisher orderBookPublisher,
            MatchService matchService) {
        super(properties, OrderCodec.getInstance());
        this.executionReportPublisher = executionReportPublisher;
        this.tradePublisher = tradePublisher;
        this.orderBookPublisher = orderBookPublisher;
        this.matchService = matchService;
    }

    @Override
    protected void onMessage(OrderProto command) {
        Order order = ProtocolModelMapper.toDomainOrder(command);
        log.info("Order received, orderId={}, userId={}", order.getOrderId(), order.getUserId());
        List<ExecutionReportProto> reports = matchService.match(order);
        for (ExecutionReportProto report : reports) {
            executionReportPublisher.offer(report);
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
            tradePublisher.offer(event);
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
            OrderBookProto snapshot = ProtocolModelMapper.toRawOrderBookSnapshot(contract, orderBook);
            orderBookPublisher.offer(snapshot);
            log.debug("Order book published for contract={}", contract);
        } catch (Exception e) {
            log.warn("Failed to publish order book for contract={}: {}", contract, e.getMessage());
        }
    }
}
