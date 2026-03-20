package com.ganten.peanuts.gateway.messaging.publisher;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockRequestCodec;
import com.ganten.peanuts.protocol.model.AeronMessage;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import com.ganten.peanuts.protocol.model.LockRequestProto;

/**
 * Publish account lock requests to account-service.
 */
@Slf4j
@Component
public class LockRequestPublisher extends AbstractAeronPublisher<LockRequestProto> {

    public LockRequestPublisher(
            @Qualifier("accountLockAeronRequestProperties") AeronProperties properties) {
        super(properties);
    }

    @PostConstruct
    public void start() {
        super.start();
    }

    @Override
    protected AeronMessage encode(LockRequestProto request) {
        return LockRequestCodec.getInstance().encode(request);
    }
}

