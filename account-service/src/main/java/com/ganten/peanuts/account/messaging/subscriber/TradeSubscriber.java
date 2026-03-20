package com.ganten.peanuts.account.messaging.subscriber;

import javax.annotation.PostConstruct;

import org.agrona.DirectBuffer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.account.mapping.TradeProtocolMapper;
import com.ganten.peanuts.account.service.AccountService;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.TradeProto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TradeSubscriber extends AbstractAeronSubscriber<TradeProto, TradeCodec> {

    private final AccountService accountService;

    public TradeSubscriber(@Qualifier("tradeSubscriber") AeronProperties aeronProperties,
            AccountService accountService) {
        super(aeronProperties, TradeCodec.getInstance());
        this.accountService = accountService;
    }

    @Override
    protected void onMessage(TradeProto message) {
        Trade trade = TradeProtocolMapper.toDomain(message);
        boolean settled = accountService.applyTrade(trade);
        if (!settled) {
            log.warn("Trade settlement skipped due to insufficient locked balance, tradeId={}", trade.getTradeId());
        }
    }
}
