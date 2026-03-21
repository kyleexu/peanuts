package com.ganten.peanuts.account.messaging.subscriber;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.account.messaging.publisher.LockResponsePublisher;
import com.ganten.peanuts.account.service.AccountService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockRequestCodec;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import com.ganten.peanuts.protocol.model.LockResponseProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Aeron: account -> order
 */
@Slf4j
@Component
public class LockRequestSubscriber extends AbstractAeronSubscriber<LockRequestProto, LockRequestCodec> {

    private final AccountService accountService;
    private final LockResponsePublisher lockResponsePublisher;

    public LockRequestSubscriber(@Qualifier("lockRequestAeronProperties") AeronProperties aeronProperties,
            LockResponsePublisher lockResponsePublisher,
            AccountService accountService) {
        super(aeronProperties, LockRequestCodec.getInstance());
        this.accountService = accountService;
        this.lockResponsePublisher = lockResponsePublisher;
    }

    /**
     * 第 4 步，消费锁请求，并返回锁响应
     * 关键: 在消费完锁请求之后，需要调用 LockResponsePublisher 发布锁响应
     */
    @Override
    protected void onMessage(LockRequestProto message) {
        boolean success = accountService.tryLock(message.getUserId(), message.getCurrency(), message.getAmount());

        LockResponseProto lockResponseProto = new LockResponseProto();
        lockResponseProto.setRequestId(message.getRequestId());
        lockResponseProto.setSuccess(success);
        lockResponseProto.setMessage(success ? "Lock request successful" : "Lock request failed");

        lockResponsePublisher.offer(lockResponseProto);
    }
}
