package com.ganten.peanuts.engine.messaging;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.aeron.AeronPollWorker;
import com.ganten.peanuts.common.entity.ExecutionReport;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.ExecType;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.engine.codec.OrderDecoder;
import com.ganten.peanuts.engine.config.MatchEngineProperties;
import com.ganten.peanuts.engine.model.OrderBook;
import com.ganten.peanuts.engine.service.MatchService;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;

@Component
public class AeronOrderSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AeronOrderSubscriber.class);

    private final MatchEngineProperties properties;
    private final AeronExecutionReportPublisher publisher;
    private final AeronTradePublisher tradePublisher;
    private final AeronOrderBookPublisher orderBookPublisher;
    private final OrderDecoder orderDecoder;
    private final MatchService matchService;

    private Subscription subscription;
    private final AtomicLong tradeIdGenerator = new AtomicLong(System.currentTimeMillis() * 1_000L);
    private AeronPollWorker pollWorker;

    public AeronOrderSubscriber(MatchEngineProperties properties, AeronExecutionReportPublisher publisher,
            AeronTradePublisher tradePublisher, AeronOrderBookPublisher orderBookPublisher, OrderDecoder orderDecoder,
            MatchService matchService) {
        this.properties = properties;
        this.publisher = publisher;
        this.tradePublisher = tradePublisher;
        this.orderBookPublisher = orderBookPublisher;
        this.orderDecoder = orderDecoder;
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
        startPollLoop();
        log.info("Order subscriber ready. channel={}, streamId={}", properties.getChannel(),
                properties.getInboundStreamId());
    }

    private void startPollLoop() {
        final FragmentHandler orderHandler = (buffer, offset, length, header) -> {
            Order order = orderDecoder.decode(buffer, offset);
            log.info("Order received, orderId={}, userId={}", order.getOrderId(), order.getUserId());
            List<ExecutionReport> reports = matchService.match(order);
            for (ExecutionReport report : reports) {
                publisher.publish(report);
            }
            publishTrades(reports);

            // 推送订单簿快照
            publishOrderBook(order.getContract());
        };
        pollWorker = AeronPollWorker.start("match-engine-order-poller",
                () -> subscription.poll(orderHandler, properties.getFragmentLimit()),
                ex -> log.error("Order subscriber poll loop failed", ex));
    }

    @PreDestroy
    public void shutdown() {
        if (pollWorker != null) {
            pollWorker.close();
        }
        if (subscription != null) {
            subscription.close();
        }
    }

    private void publishTrades(List<ExecutionReport> reports) {
        for (int i = 0; i < reports.size() - 1; i++) {
            ExecutionReport first = reports.get(i);
            ExecutionReport second = reports.get(i + 1);

            ExecutionReport buyReport;
            ExecutionReport sellReport;
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
            tradePublisher.publish(trade);
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
            orderBookPublisher.publish(contract, orderBook);
            log.debug("Order book published for contract={}", contract);
        } catch (Exception e) {
            log.warn("Failed to publish order book for contract={}: {}", contract, e.getMessage());
        }
    }
}
