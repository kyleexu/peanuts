package com.ganten.peanuts.market.messaging.subscriber;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.market.config.MarketAeronProperties;
import com.ganten.peanuts.market.mapping.TradeProtocolMapper;
import com.ganten.peanuts.market.service.MarketDataService;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.model.TradeProto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketTradeAeronSubscriber extends AbstractAeronSubscriber<TradeProto, TradeCodec> {

    private final MarketDataService marketDataService;

    public MarketTradeAeronSubscriber(@Qualifier("tradeSubscriber") AeronProperties aeronProperties,
            MarketDataService marketDataService) {
        super(aeronProperties, TradeCodec.getInstance());
        this.marketDataService = marketDataService;
    }

    @Override
    protected void onMessage(TradeProto message) {
        Trade trade = TradeProtocolMapper.toDomain(message);
        marketDataService.onTrade(trade);
    }
}
