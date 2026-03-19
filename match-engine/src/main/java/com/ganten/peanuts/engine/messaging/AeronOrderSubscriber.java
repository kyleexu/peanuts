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
import com.ganten.peanuts.common.enums.ExecType;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.engine.codec.OrderDecoder;
import com.ganten.peanuts.engine.config.MatchEngineProperties;
import com.ganten.peanuts.engine.service.MatchService;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;

@Component
public class AeronOrderSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AeronOrderSubscriber.class);

    private final MatchEngineProperties properties;
    private final AeronExecutionReportPublisher publisher;
    private final AeronTradePublisher tradePublisher;
    private final OrderDecoder orderDecoder;
    private final MatchService matchService;

    private Subscription subscription;
    private final AtomicLong tradeIdGenerator = new AtomicLong(System.currentTimeMillis() * 1_000L);
    private AeronPollWorker pollWorker;

    public AeronOrderSubscriber(MatchEngineProperties properties, AeronExecutionReportPublisher publisher,
            AeronTradePublisher tradePublisher, OrderDecoder orderDecoder, MatchService matchService) {
        this.properties = properties;
        this.publisher = publisher;
        this.tradePublisher = tradePublisher;
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
                Trade trade = toTrade(report);
                if (trade != null) {
                    tradePublisher.publish(trade);
                }
            }
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

    private Trade toTrade(ExecutionReport report) {
        if (report.getExecType() != ExecType.TRADE || report.getMatchedQuantity() == null
                || report.getMatchedQuantity().signum() <= 0) {
            return null;
        }

        Trade trade = new Trade();
        trade.setTradeId(tradeIdGenerator.incrementAndGet());
        if (report.getSide() == Side.BUY) {
            trade.setBuyOrderId(report.getOrderId());
            trade.setSellOrderId(report.getCounterpartyOrderId());
        } else {
            trade.setBuyOrderId(report.getCounterpartyOrderId());
            trade.setSellOrderId(report.getOrderId());
        }
        trade.setContract(report.getContract());
        trade.setPrice(report.getMatchedPrice());
        trade.setQuantity(report.getMatchedQuantity());
        trade.setTimestamp(report.getTimestamp());
        return trade;
    }
}
