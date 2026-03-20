package com.ganten.peanuts.engine.messaging;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.engine.config.MatchEngineProperties;
import com.ganten.peanuts.engine.model.OrderBook;
import com.ganten.peanuts.protocol.codec.OrderBookEncoder;
import com.ganten.peanuts.protocol.model.EncodedMessage;
import io.aeron.Aeron;
import io.aeron.Publication;

/**
 * 订单簿 Aeron 发布者，用于推送订单簿快照到 Aeron channel
 */
@Component
public class AeronOrderBookPublisher {

    private static final Logger log = LoggerFactory.getLogger(AeronOrderBookPublisher.class);

    private final MatchEngineProperties properties;
    private final OrderBookEncoder encoder;

    private Aeron aeron;
    private Publication publication;

    public AeronOrderBookPublisher(MatchEngineProperties properties, OrderBookEncoder encoder) {
        this.properties = properties;
        this.encoder = encoder;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.warn("Match-engine Aeron order book publisher disabled");
            return;
        }

        // MediaDriver 通常由 ExecutionReportPublisher 启动，这里直接连接
        Aeron.Context context = new Aeron.Context();
        context.aeronDirectoryName(properties.getDirectory());
        aeron = Aeron.connect(context);
        publication = aeron.addPublication(properties.getChannel(), properties.getOrderBookStreamId());
        log.info("Order book publisher ready. dir={}, channel={}, streamId={}", properties.getDirectory(),
                properties.getChannel(), properties.getOrderBookStreamId());
    }

    /**
     * 发布订单簿快照
     *
     * @param contract 合约
     * @param orderBook 订单簿
     */
    public void publish(Contract contract, OrderBook orderBook) {
        if (publication == null) {
            log.error("Order book publication not available, contract={}", contract);
            return;
        }

        try {
            EncodedMessage msg = encoder.encode(contract, orderBook.getBuyOrders(), orderBook.getSellOrders());
            long result = publication.offer(msg.getBuffer(), 0, msg.getLength());

            if (result < 0) {
                log.warn("Failed to publish order book, contract={}, result={}", contract, result);
            }
        } catch (Exception e) {
            log.error("Error publishing order book, contract={}", contract, e);
        }
    }

    public Aeron aeron() {
        return aeron;
    }

    @PreDestroy
    public void shutdown() {
        if (publication != null) {
            publication.close();
        }
        if (aeron != null) {
            aeron.close();
        }
    }
}
