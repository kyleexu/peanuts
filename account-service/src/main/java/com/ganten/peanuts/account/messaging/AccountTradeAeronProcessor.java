package com.ganten.peanuts.account.messaging;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.account.config.AccountAeronProperties;
import com.ganten.peanuts.account.mapping.TradeProtocolMapper;
import com.ganten.peanuts.account.service.AccountService;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.protocol.aeron.AeronPollWorker;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.TradeProto;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AccountTradeAeronProcessor {

    private final AccountAeronProperties properties;
    private final AccountService accountService;
    private final TradeProtocolMapper tradeProtocolMapper;

    private Aeron aeron;
    private Subscription tradeSubscription;
    private AeronPollWorker pollWorker;

    public AccountTradeAeronProcessor(AccountAeronProperties properties, AccountService accountService,
            TradeProtocolMapper tradeProtocolMapper) {
        this.properties = properties;
        this.accountService = accountService;
        this.tradeProtocolMapper = tradeProtocolMapper;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        aeron = Aeron.connect();
        tradeSubscription = aeron.addSubscription(properties.getChannel(), properties.getTradeStreamId());
        startPollLoop();
        log.info("Account trade Aeron processor ready. channel={}, tradeStream={}", properties.getChannel(),
                properties.getTradeStreamId());
    }

    private void startPollLoop() {
        final FragmentHandler tradeHandler = (buffer, offset, length, header) -> {
            TradeProto event = TradeCodec.getInstance().decode(buffer, offset);
            Trade trade = tradeProtocolMapper.toDomain(event);
            boolean settled = accountService.applyTrade(trade);
            if (!settled) {
                log.warn("Trade settlement skipped due to insufficient locked balance, tradeId={}", trade.getTradeId());
            }
        };
        pollWorker = AeronPollWorker.start("account-trade-aeron-poller", () -> tradeSubscription.poll(tradeHandler, 50),
                ex -> log.error("Account trade poll loop failed", ex));
    }

    @PreDestroy
    public void shutdown() {
        if (pollWorker != null) {
            pollWorker.close();
        }
        if (tradeSubscription != null) {
            tradeSubscription.close();
        }
        if (aeron != null) {
            aeron.close();
        }
    }
}
