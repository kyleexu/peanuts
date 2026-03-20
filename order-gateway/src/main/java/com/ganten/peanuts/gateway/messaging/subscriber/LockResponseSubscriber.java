package com.ganten.peanuts.gateway.messaging.subscriber;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.gateway.account.LockPendingRequests;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockResponseCodec;
import com.ganten.peanuts.protocol.model.LockResponseProto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LockResponseSubscriber extends AbstractAeronSubscriber<LockResponseProto, LockResponseCodec> {

    private final LockPendingRequests pendingRequests;

    public LockResponseSubscriber(
            @Qualifier("accountLockAeronResponseProperties") AeronProperties aeronProperties,
            LockPendingRequests pendingRequests) {
        super(aeronProperties, LockResponseCodec.getInstance());
        this.pendingRequests = pendingRequests;
    }

    /**
     * 第 5 步，消费锁响应，并完成锁请求
     * 关键: 在消费完锁响应之后，需要调用 LockPendingRequests 完成锁请求
     */
    @Override
    protected void onMessage(LockResponseProto message) {
        pendingRequests.complete(message);
    }
}
