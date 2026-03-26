package com.ganten.peanuts.gateway.messaging.publisher;

import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.protocol.codec.OrderCodec;
import com.ganten.peanuts.protocol.model.OrderProto;
import lombok.extern.slf4j.Slf4j;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;

@Slf4j
@Component
public class OrderPublisher extends AbstractAeronPublisher<OrderProto, OrderCodec> {

    public OrderPublisher() {
        super(AeronStream.ORDER.toProperties(), OrderCodec.getInstance());
    }
}
