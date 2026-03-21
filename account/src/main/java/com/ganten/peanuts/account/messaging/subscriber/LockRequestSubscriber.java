package com.ganten.peanuts.account.messaging.subscriber;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
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
 * <p>
 * {@code account.raft.enabled=false}：仅 Aeron，走 {@link #onMessage}。<br>
 * {@code account.raft.enabled=true}：propose 后由 {@link #onRaftCommitted} 与
 * {@link #onMessage} 共用 {@link #applyLockRequest}（跟随者仅锁、不回包）。
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

    @Override
    protected void onRaftRejected(LockRequestProto message, String reason) {
        LockResponseProto lockResponseProto = new LockResponseProto();
        lockResponseProto.setRequestId(message.getRequestId());
        lockResponseProto.setSuccess(false);
        lockResponseProto.setMessage(reason);
        lockResponsePublisher.offer(lockResponseProto);
    }

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
