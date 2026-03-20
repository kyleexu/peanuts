package com.ganten.peanuts.engine.messaging.publisher;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.engine.config.MatchEngineProperties;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.TradeProto;
import lombok.extern.slf4j.Slf4j;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import com.ganten.peanuts.protocol.model.AeronMessage;

@Slf4j
@Component
public class AeronTradePublisher extends AbstractAeronPublisher<TradeProto> {

    private final MatchEngineProperties properties;
    private final AeronExecutionReportPublisher reportPublisher;

    public AeronTradePublisher(MatchEngineProperties properties, AeronExecutionReportPublisher reportPublisher) {
        this.properties = properties;
        this.reportPublisher = reportPublisher;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        if (reportPublisher.aeron() == null) {
            log.warn("Skip trade publisher init because Aeron connection is unavailable");
            return;
        }

        setPublication(reportPublisher.aeron().addPublication(properties.getChannel(), properties.getTradeStreamId()));
        log.info("Trade publisher ready. channel={}, streamId={}", properties.getChannel(),
                properties.getTradeStreamId());
    }

    @Override
    protected AeronMessage encode(TradeProto message) {
        return TradeCodec.getInstance().encode(message);
    }

    @Override
    protected void onPublicationUnavailable(TradeProto trade) {
        log.error("Trade publication not available, tradeId={}", trade.getTradeId());
    }

    @Override
    protected void onOfferResult(TradeProto trade, long result) {
        if (result > 0) {
            log.info("Trade published, tradeId={}, buyOrderId={}, sellOrderId={}, result={}", trade.getTradeId(),
                    trade.getBuyOrderId(), trade.getSellOrderId(), result);
        } else {
            log.warn("Trade publish back pressured, tradeId={}, code={}", trade.getTradeId(), result);
        }
    }

    @PreDestroy
    public void shutdown() {
        super.shutdown();
    }
}
