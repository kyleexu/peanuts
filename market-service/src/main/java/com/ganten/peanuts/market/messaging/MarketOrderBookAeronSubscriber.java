package com.ganten.peanuts.market.messaging;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.aeron.AeronPollWorker;
import com.ganten.peanuts.market.config.MarketAeronProperties;
import com.ganten.peanuts.market.service.OrderBookAggregationService;
import com.ganten.peanuts.protocol.codec.OrderBookMessageCodec;
import com.ganten.peanuts.protocol.model.RawOrderBookSnapshot;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketOrderBookAeronSubscriber {

    private final MarketAeronProperties properties;
    private final OrderBookMessageCodec orderBookMessageCodec;
    private final OrderBookAggregationService orderBookAggregationService;

    private Aeron aeron;
    private Subscription subscription;
    private AeronPollWorker pollWorker;

    public MarketOrderBookAeronSubscriber(MarketAeronProperties properties, OrderBookMessageCodec orderBookMessageCodec,
            OrderBookAggregationService orderBookAggregationService) {
        this.properties = properties;
        this.orderBookMessageCodec = orderBookMessageCodec;
        this.orderBookAggregationService = orderBookAggregationService;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        aeron = Aeron.connect();
        subscription = aeron.addSubscription(properties.getChannel(), properties.getOrderBookStreamId());
        startPollLoop();
        log.info("Market order-book subscriber ready. channel={}, streamId={}", properties.getChannel(),
                properties.getOrderBookStreamId());
    }

    private void startPollLoop() {
        final FragmentHandler orderBookHandler = (buffer, offset, length, header) -> {
            RawOrderBookSnapshot snapshot = orderBookMessageCodec.decode(buffer, offset);
            orderBookAggregationService.onOrderBook(snapshot);
        };
        pollWorker = AeronPollWorker.start("market-orderbook-aeron-poller",
                () -> subscription.poll(orderBookHandler, properties.getFragmentLimit()),
                ex -> log.error("Market order-book poll loop failed", ex));
    }

    @PreDestroy
    public void shutdown() {
        if (pollWorker != null) {
            pollWorker.close();
        }
        if (subscription != null) {
            subscription.close();
        }
        if (aeron != null) {
            aeron.close();
        }
    }
}
