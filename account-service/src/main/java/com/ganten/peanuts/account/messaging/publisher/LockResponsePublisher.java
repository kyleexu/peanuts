package com.ganten.peanuts.account.messaging.publisher;

import org.springframework.beans.factory.annotation.Qualifier;

import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockResponseCodec;
import com.ganten.peanuts.protocol.model.LockResponseProto;

public class LockResponsePublisher extends AbstractAeronPublisher<LockResponseProto, LockResponseCodec> {

    public LockResponsePublisher(@Qualifier("lockResponsePublisher") AeronProperties properties) {
        super(properties, LockResponseCodec.getInstance());
    }
    
}
