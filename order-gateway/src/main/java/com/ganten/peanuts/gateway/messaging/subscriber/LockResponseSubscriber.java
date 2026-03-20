package com.ganten.peanuts.gateway.messaging.subscriber;

import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import com.ganten.peanuts.gateway.account.AccountLockPendingRequests;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockResponseCodec;
import com.ganten.peanuts.protocol.model.LockResponseProto;

@Slf4j
@Component
public class LockResponseSubscriber extends AbstractAeronSubscriber<LockResponseProto, LockResponseCodec> {

    private final AccountLockPendingRequests pendingRequests;

    public LockResponseSubscriber(
            @Qualifier("lockResponseSubscriber") AeronProperties aeronProperties,
            AccountLockPendingRequests pendingRequests) {
        super(aeronProperties, LockResponseCodec.getInstance());
        this.pendingRequests = pendingRequests;
    }

    @Override
    protected void onMessage(LockResponseProto message) {
        pendingRequests.complete(message);
    }
}
