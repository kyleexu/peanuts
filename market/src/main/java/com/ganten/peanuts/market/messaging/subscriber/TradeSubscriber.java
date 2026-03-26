package com.ganten.peanuts.market.messaging.subscriber;

import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.entity.AeronProperties;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.common.enums.RaftApplyMode;
import com.ganten.peanuts.market.mapping.TradeProtocolMapper;
import com.ganten.peanuts.market.service.CandleService;
import com.ganten.peanuts.market.service.TradeService;
import com.ganten.peanuts.market.service.TickerService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.TradeProto;

import lombok.extern.slf4j.Slf4j;

/**
 * TradeSubscriber 用于消费成交，并更新市场数据，这里是更新 Ticker 和 K线数据
 */
@Slf4j
@Component
public class TradeSubscriber extends AbstractAeronSubscriber<TradeProto, TradeCodec> {
    private static final String INSTANCE_TAG = "market";

    private final TickerService tickerService;
    private final CandleService candleService;
    private final TradeService tradeService;

    public TradeSubscriber(TickerService tickerService,
            CandleService candleService,
            TradeService tradeService) {
        super(buildProperties(), TradeCodec.getInstance());
        this.tickerService = tickerService;
        this.candleService = candleService;
        this.tradeService = tradeService;
    }

    private static AeronProperties buildProperties() {
        AeronProperties properties = AeronStream.TRADE.toProperties(INSTANCE_TAG);
        properties.setEnableRaft(false);
        properties.setRaftApplyMode(RaftApplyMode.DISABLE);
        return properties;
    }

    /**
     * 第 16 步，消费成交，并更新市场数据
     * 关键: 这里需要使用 tickerService 和 candleService 更新市场数据，然后推送到 WebSocket 客户端
     */
    @Override
    protected void onMessage(TradeProto message) {
        Trade trade = TradeProtocolMapper.toDomain(message);
        tradeService.onTrade(trade);
        tickerService.onTrade(trade);
        candleService.onTrade(trade);
    }
}
