package com.ganten.peanuts.market.messaging.subscriber;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.market.config.MarketAeronProperties;
import com.ganten.peanuts.market.service.OrderBookAggregationService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.codec.OrderBookCodec;
import com.ganten.peanuts.protocol.model.OrderBookSnapshotProto;

import io.aeron.Aeron;
import io.aeron.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;

@Slf4j
@Component
public class MarketOrderBookAeronSubscriber extends AbstractAeronSubscriber<OrderBookSnapshotProto> {

    private final MarketAeronProperties properties;
    private final OrderBookAggregationService orderBookAggregationService;

    private Aeron aeron;

    public MarketOrderBookAeronSubscriber(MarketAeronProperties properties,
            OrderBookAggregationService orderBookAggregationService) {
        this.properties = properties;
        this.orderBookAggregationService = orderBookAggregationService;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        aeron = Aeron.connect();
        Subscription subscription = aeron.addSubscription(properties.getChannel(), properties.getOrderBookStreamId());
        start("market-orderbook-aeron-poller", subscription, properties.getFragmentLimit(),
                ex -> log.error("Market order-book poll loop failed", ex));
        log.info("Market order-book subscriber ready. channel={}, streamId={}", properties.getChannel(),
                properties.getOrderBookStreamId());
    }

    @PreDestroy
    public void shutdown() {
        super.shutdown();
        if (aeron != null) {
            aeron.close();
        }
    }

    @Override
    protected OrderBookSnapshotProto decode(DirectBuffer buffer, int offset) {
        return OrderBookCodec.getInstance().decode(buffer, offset);
    }

    @Override
    protected void onMessage(OrderBookSnapshotProto snapshot) {
        orderBookAggregationService.onOrderBook(snapshot);
    }
}
