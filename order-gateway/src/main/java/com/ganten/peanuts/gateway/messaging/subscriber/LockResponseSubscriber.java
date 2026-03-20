package com.ganten.peanuts.gateway.messaging.subscriber;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import com.ganten.peanuts.gateway.account.AccountLockPendingRequests;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockResponseCodec;
import com.ganten.peanuts.protocol.model.LockResponseProto;
import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.DirectBuffer;

@Slf4j
@Component
public class LockResponseSubscriber extends AbstractAeronSubscriber<LockResponseProto> {

    private final AccountLockPendingRequests pendingRequests;

    private Aeron aeron;

    public LockResponseSubscriber(
            @Qualifier("accountLockAeronResponseProperties") AeronProperties responseAeronProperties,
            AccountLockPendingRequests pendingRequests) {
        super(responseAeronProperties);
        this.pendingRequests = pendingRequests;
    }

    @PostConstruct
    public void start() {
        start("gateway-account-lock-response-poller",
                aeron.addSubscription(properties.getChannel(), properties.getStreamId()),
                properties.getFragmentLimit(),
                ex -> log.error("Account lock response poll loop failed", ex));
        log.info("Account lock response subscriber ready. channel={}, streamId={}",
                properties.getChannel(), properties.getStreamId());
    }

    @Override
    protected LockResponseProto decode(DirectBuffer buffer, int offset) {
        return LockResponseCodec.getInstance().decode(buffer, offset);
    }

    @Override
    protected void onMessage(LockResponseProto message) {
        pendingRequests.complete(message);
    }
}
