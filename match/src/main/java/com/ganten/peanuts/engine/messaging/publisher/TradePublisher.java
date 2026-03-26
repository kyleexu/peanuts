package com.ganten.peanuts.engine.messaging.publisher;

import org.springframework.stereotype.Component;
import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.protocol.codec.TradeCodec;
import com.ganten.peanuts.protocol.model.TradeProto;
import lombok.extern.slf4j.Slf4j;

import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import com.ganten.peanuts.protocol.model.AeronMessage;

@Slf4j
@Component
public class TradePublisher extends AbstractAeronPublisher<TradeProto, TradeCodec> {

    public TradePublisher() {
        super(AeronStream.TRADE.toProperties(), TradeCodec.getInstance());
    }

    @Override
    protected AeronMessage encode(TradeProto message) {
        return TradeCodec.getInstance().encode(message);
    }
}
