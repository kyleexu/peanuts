package com.ganten.peanuts.account.messaging.publisher;

import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.protocol.aeron.AbstractAeronPublisher;
import com.ganten.peanuts.protocol.codec.LockResponseCodec;
import com.ganten.peanuts.protocol.model.LockResponseProto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LockResponsePublisher extends AbstractAeronPublisher<LockResponseProto, LockResponseCodec> {

    public LockResponsePublisher() {
        super(AeronStream.LOCK_RESPONSE.toProperties(), LockResponseCodec.getInstance());
    }

}
