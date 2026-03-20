package com.ganten.peanuts.gateway.messaging.publisher;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.gateway.config.AeronProperties;
import com.ganten.peanuts.gateway.mapping.OrderProtocolMapper;
import com.ganten.peanuts.protocol.codec.OrderCodec;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.model.OrderProto;
import io.aeron.Aeron;
import io.aeron.Publication;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AeronOrderPublisher  {

    private final AeronProperties aeronProperties;

    private Aeron aeron;
    private Publication publication;

    public AeronOrderPublisher(AeronProperties aeronProperties) {
        this.aeronProperties = aeronProperties;
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


    public void publish(Order order) {
        if (!aeronProperties.isEnabled()) {
            log.debug("Skip dispatch because Aeron is disabled, orderId={}", order.getOrderId());
            return;
        }
        if (publication == null) {
            log.error("Publication not available, orderId={}", order.getOrderId());
            return;
        }

        OrderProto proto = OrderProtocolMapper.toProto(order);
        AeronMessage encoded = OrderCodec.getInstance().encode(proto);
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
