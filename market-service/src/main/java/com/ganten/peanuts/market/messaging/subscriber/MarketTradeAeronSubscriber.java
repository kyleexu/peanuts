package com.ganten.peanuts.market.messaging.subscriber;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.market.config.MarketAeronProperties;
import com.ganten.peanuts.market.mapping.TradeProtocolMapper;
import com.ganten.peanuts.market.service.MarketDataService;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.model.TradeProto;
import io.aeron.Aeron;
import org.agrona.DirectBuffer;
import io.aeron.Subscription;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketTradeAeronSubscriber extends AbstractAeronSubscriber<TradeProto> {

    private final MarketAeronProperties properties;
    private final MarketDataService marketDataService;
    private final TradeProtocolMapper tradeProtocolMapper;

    private Aeron aeron;

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
        Subscription subscription = aeron.addSubscription(properties.getChannel(), properties.getTradeStreamId());
        start("market-trade-aeron-poller", subscription, properties.getFragmentLimit(),
                ex -> log.error("Market trade poll loop failed", ex));
        log.info("Market trade subscriber ready. channel={}, streamId={}", properties.getChannel(),
                properties.getTradeStreamId());
    }

    @PreDestroy
    public void shutdown() {
        super.shutdown();
        if (aeron != null) {
            aeron.close();
        }
    }

    @Override
    protected TradeProto decode(DirectBuffer buffer, int offset) {
        return TradeCodec.getInstance().decode(buffer, offset);
    }

    @Override
    protected void onMessage(TradeProto event) {
        Trade trade = tradeProtocolMapper.toDomain(event);
        marketDataService.onTrade(trade);
    }
}
