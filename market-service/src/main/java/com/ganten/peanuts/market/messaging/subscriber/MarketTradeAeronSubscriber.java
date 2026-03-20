package com.ganten.peanuts.market.messaging.subscriber;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.aeron.AeronPollWorker;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.market.config.MarketAeronProperties;
import com.ganten.peanuts.market.mapping.TradeProtocolMapper;
import com.ganten.peanuts.market.service.MarketDataService;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.TradeProto;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketTradeAeronSubscriber {

    private final MarketAeronProperties properties;
    private final MarketDataService marketDataService;
    private final TradeProtocolMapper tradeProtocolMapper;

    private Aeron aeron;
    private Subscription subscription;
    private AeronPollWorker pollWorker;

    public MarketTradeAeronSubscriber(MarketAeronProperties properties,
            MarketDataService marketDataService, TradeProtocolMapper tradeProtocolMapper) {
        this.properties = properties;
        this.marketDataService = marketDataService;
        this.tradeProtocolMapper = tradeProtocolMapper;
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
            TradeProto event = TradeCodec.getInstance().decode(buffer, offset);
            Trade trade = tradeProtocolMapper.toDomain(event);
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
