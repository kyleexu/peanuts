package com.ganten.peanuts.gateway.messaging.publisher;

import org.springframework.stereotype.Component;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.OrderCodec;
import com.ganten.peanuts.protocol.model.OrderProto;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;

@Slf4j
@Component
public class OrderPublisher extends AbstractAeronPublisher<OrderProto, OrderCodec> {

    public OrderPublisher(@Qualifier("orderDispatchAeronProperties") AeronProperties aeronProperties) {
        super(aeronProperties,OrderCodec.getInstance());
    }
}
