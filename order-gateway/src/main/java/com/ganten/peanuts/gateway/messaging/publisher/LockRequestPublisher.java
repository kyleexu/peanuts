package com.ganten.peanuts.gateway.messaging.publisher;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockRequestCodec;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import com.ganten.peanuts.protocol.model.LockRequestProto;

/**
 * Publish account lock requests to account-service.
 */
@Slf4j
@Component
public class LockRequestPublisher extends AbstractAeronPublisher<LockRequestProto, LockRequestCodec> {

    public LockRequestPublisher(
            @Qualifier("accountLockAeronRequestProperties") AeronProperties properties) {
        super(properties, LockRequestCodec.getInstance());
    }
}
