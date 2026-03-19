package com.ganten.peanuts.gateway.dispatcher;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.gateway.OrderEncoder;
import com.ganten.peanuts.gateway.config.AeronProperties;
import com.ganten.peanuts.gateway.model.EncodedOrder;
import io.aeron.Aeron;
import io.aeron.Publication;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AeronOrderDispatcher implements OrderDispatcher {

    private final AeronProperties aeronProperties;
    private final OrderEncoder orderEncoder;

    private Aeron aeron;
    private Publication publication;

    public AeronOrderDispatcher(AeronProperties aeronProperties, OrderEncoder orderEncoder) {
        this.aeronProperties = aeronProperties;
        this.orderEncoder = orderEncoder;
    }

    @PostConstruct
    public void init() {
        if (!aeronProperties.isEnabled()) {
            log.warn("Aeron dispatcher disabled by config");
            return;
        }
        try {
            aeron = Aeron.connect();
            publication = aeron.addPublication(aeronProperties.getChannel(), aeronProperties.getStreamId());
            log.info("Aeron dispatcher ready. channel={}, streamId={}", aeronProperties.getChannel(),
                    aeronProperties.getStreamId());
        } catch (Exception e) {
            log.error("Failed to initialize Aeron dispatcher", e);
        }
    }

    @Override
    public void dispatch(Order order) {
        if (!aeronProperties.isEnabled()) {
            log.debug("Skip dispatch because Aeron is disabled, orderId={}", order.getOrderId());
            return;
        }
        if (publication == null) {
            log.error("Publication not available, orderId={}", order.getOrderId());
            return;
        }

        EncodedOrder encoded = orderEncoder.encode(order);
        long result = publication.offer(encoded.getBuffer(), 0, encoded.getLength());
        if (result > 0) {
            log.info("Dispatched order successfully, orderId={}, result={}", order.getOrderId(), result);
        } else {
            log.warn("Dispatch back pressured, orderId={}, code={}", order.getOrderId(), result);
        }
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
