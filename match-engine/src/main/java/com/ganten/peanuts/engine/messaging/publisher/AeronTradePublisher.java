package com.ganten.peanuts.engine.messaging.publisher;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.engine.config.MatchEngineProperties;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.model.TradeProto;
import io.aeron.Publication;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AeronTradePublisher {

    private final MatchEngineProperties properties;
    private final AeronExecutionReportPublisher reportPublisher;

    private Publication publication;

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

        publication = reportPublisher.aeron().addPublication(properties.getChannel(), properties.getTradeStreamId());
        log.info("Trade publisher ready. channel={}, streamId={}", properties.getChannel(),
                properties.getTradeStreamId());
    }

    public void publish(TradeProto trade) {
        if (publication == null) {
            log.error("Trade publication not available, tradeId={}", trade.getTradeId());
            return;
        }

        AeronMessage encodedMessage = TradeCodec.getInstance().encode(trade);
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
