package com.ganten.peanuts.engine.messaging.publisher;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.engine.config.MatchEngineProperties;
import com.ganten.peanuts.protocol.codec.OrderBookCodec;
import com.ganten.peanuts.protocol.model.OrderBookSnapshotProto;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import com.ganten.peanuts.protocol.model.AeronMessage;

import lombok.extern.slf4j.Slf4j;
import io.aeron.Aeron;

/**
 * 订单簿 Aeron 发布者，用于推送订单簿快照到 Aeron channel
 */
@Slf4j
@Component
public class AeronOrderBookPublisher extends AbstractAeronPublisher<OrderBookSnapshotProto> {


    private final MatchEngineProperties properties;

    private Aeron aeron;

    public AeronOrderBookPublisher(MatchEngineProperties properties) {
        this.properties = properties;
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
        setPublication(aeron.addPublication(properties.getChannel(), properties.getOrderBookStreamId()));
        log.info("Order book publisher ready. dir={}, channel={}, streamId={}", properties.getDirectory(),
                properties.getChannel(), properties.getOrderBookStreamId());
    }

    @Override
    protected AeronMessage encode(OrderBookSnapshotProto snapshot) {
        return OrderBookCodec.getInstance().encode(snapshot);
    }

    @Override
    protected void onPublicationUnavailable(OrderBookSnapshotProto snapshot) {
        log.error("Order book publication not available, contract={}", snapshot.getContract());
    }

    @Override
    protected void onOfferResult(OrderBookSnapshotProto snapshot, long result) {
        if (result < 0) {
            log.warn("Failed to publish order book, contract={}, result={}", snapshot.getContract(), result);
        }
    }

    @Override
    protected void onPublishError(OrderBookSnapshotProto snapshot, Throwable error) {
        log.error("Error publishing order book, contract={}", snapshot.getContract(), error);
    }

    public Aeron aeron() {
        return aeron;
    }

    @PreDestroy
    public void shutdown() {
        super.shutdown();
        if (aeron != null) {
            aeron.close();
        }
    }
}
