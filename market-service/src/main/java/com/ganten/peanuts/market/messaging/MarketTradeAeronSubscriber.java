package com.ganten.peanuts.market.messaging;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.aeron.AeronPollWorker;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.market.codec.TradeMessageCodec;
import com.ganten.peanuts.market.config.MarketAeronProperties;
import com.ganten.peanuts.market.service.MarketDataService;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketTradeAeronSubscriber {

    private final MarketAeronProperties properties;
    private final TradeMessageCodec tradeMessageCodec;
    private final MarketDataService marketDataService;

    private Aeron aeron;
    private Subscription subscription;
    private AeronPollWorker pollWorker;

    public MarketTradeAeronSubscriber(MarketAeronProperties properties, TradeMessageCodec tradeMessageCodec,
            MarketDataService marketDataService) {
        this.properties = properties;
        this.tradeMessageCodec = tradeMessageCodec;
        this.marketDataService = marketDataService;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        aeron = Aeron.connect();
        subscription = aeron.addSubscription(properties.getChannel(), properties.getTradeStreamId());
        startPollLoop();
        log.info("Market trade subscriber ready. channel={}, streamId={}", properties.getChannel(),
                properties.getTradeStreamId());
    }

    private void startPollLoop() {
        final FragmentHandler tradeHandler = (buffer, offset, length, header) -> {
            Trade trade = tradeMessageCodec.decode(buffer, offset);
            marketDataService.onTrade(trade);
        };
        pollWorker = AeronPollWorker.start("market-trade-aeron-poller",
                () -> subscription.poll(tradeHandler, properties.getFragmentLimit()),
                ex -> log.error("Market trade poll loop failed", ex));
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
