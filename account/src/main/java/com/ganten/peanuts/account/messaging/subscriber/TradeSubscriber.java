package com.ganten.peanuts.account.messaging.subscriber;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.account.mapping.TradeProtocolMapper;
import com.ganten.peanuts.account.service.AccountService;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.util.DecimalLogFormatter;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.TradeProto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TradeSubscriber extends AbstractAeronSubscriber<TradeProto, TradeCodec> {

    private final AccountService accountService;

    public TradeSubscriber(@Qualifier("tradeAeronProperties") AeronProperties aeronProperties,
            AccountService accountService) {
        super(aeronProperties, TradeCodec.getInstance());
        this.accountService = accountService;
    }

    /**
     * 第 15 步，消费成交，并结算成交
     * 关键: 这里需要使用 accountService 结算成交
     */
    @Override
    protected void onMessage(TradeProto message) {
        Trade trade = TradeProtocolMapper.toDomain(message);
        log.info("接收到成交记录, tradeId={}, buyOrderId={}, sellOrderId={}, price={}, quantity={}, ts={}", trade.getTradeId(),
                trade.getBuyOrderId(),
                trade.getSellOrderId(),
                DecimalLogFormatter.p4(trade.getPrice()),
                DecimalLogFormatter.p4(trade.getQuantity()),
                trade.getTimestamp());
        boolean settled = accountService.applyTrade(trade);
        if (!settled) {
            log.warn("Trade settlement skipped due to insufficient locked balance, tradeId={}", trade.getTradeId());
        }
    }
}
