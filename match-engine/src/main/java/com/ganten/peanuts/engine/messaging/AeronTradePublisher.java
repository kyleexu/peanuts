package com.ganten.peanuts.engine.messaging;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.engine.codec.TradeEncoder;
import com.ganten.peanuts.engine.config.MatchEngineProperties;
import com.ganten.peanuts.engine.model.EncodedMessage;
import io.aeron.Publication;

@Component
public class AeronTradePublisher {

    private static final Logger log = LoggerFactory.getLogger(AeronTradePublisher.class);

    private final MatchEngineProperties properties;
    private final AeronExecutionReportPublisher reportPublisher;
    private final TradeEncoder encoder;

    private Publication publication;

    public AeronTradePublisher(MatchEngineProperties properties, AeronExecutionReportPublisher reportPublisher,
            TradeEncoder encoder) {
        this.properties = properties;
        this.reportPublisher = reportPublisher;
        this.encoder = encoder;
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

        publication = reportPublisher.aeron().addPublication(properties.getChannel(), properties.getTradeStreamId());
        log.info("Trade publisher ready. channel={}, streamId={}", properties.getChannel(),
                properties.getTradeStreamId());
    }

    public void publish(Trade trade) {
        if (publication == null) {
            log.error("Trade publication not available, tradeId={}", trade.getTradeId());
            return;
        }

        EncodedMessage encodedMessage = encoder.encode(trade);
        long result = publication.offer(encodedMessage.getBuffer(), 0, encodedMessage.getLength());
        if (result > 0) {
            log.info("Trade published, tradeId={}, buyOrderId={}, sellOrderId={}, result={}", trade.getTradeId(),
                    trade.getBuyOrderId(), trade.getSellOrderId(), result);
        } else {
            log.warn("Trade publish back pressured, tradeId={}, code={}", trade.getTradeId(), result);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (publication != null) {
            publication.close();
        }
    }
}
