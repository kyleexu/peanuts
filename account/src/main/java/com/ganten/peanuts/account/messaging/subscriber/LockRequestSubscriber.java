package com.ganten.peanuts.account.messaging.subscriber;

import org.springframework.stereotype.Component;

import com.ganten.peanuts.account.messaging.publisher.LockResponsePublisher;
import com.ganten.peanuts.account.service.AccountService;
import com.ganten.peanuts.common.enums.AeronStream;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.codec.LockRequestCodec;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import com.ganten.peanuts.protocol.model.LockResponseProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Aeron: account -> order
 * <p>
 * 无 Raft：{@link #onMessage} 走内存 {@link AccountService#tryLock}。<br>
 * 有 Raft：仅将编码后的命令通过
 * {@link com.ganten.peanuts.protocol.raft.RaftBootstrap#apply(byte[])} 写入
 * Raft 日志（propose），
 * 不在此链路处理业务；propose 被 leader 接受后由 {@link #onRaftAccepted} 发回执。
 */
@Slf4j
@Component
public class LockRequestSubscriber extends AbstractAeronSubscriber<LockRequestProto, LockRequestCodec> {

    private final AccountService accountService;
    private final LockResponsePublisher lockResponsePublisher;

    public LockRequestSubscriber(LockResponsePublisher lockResponsePublisher,
            AccountService accountService) {
        super(AeronStream.LOCK_REQUEST.toProperties(), LockRequestCodec.getInstance());
        this.accountService = accountService;
        this.lockResponsePublisher = lockResponsePublisher;
    }

    @Override
    protected void onMessage(LockRequestProto message) {
        boolean success = accountService.tryLock(message.getUserId(), message.getCurrency(), message.getAmount());
        log.info("Lock request completed, requestId={}, request={}, success={}", message.getRequestId(), message,
                success);
        LockResponseProto lockResponseProto = new LockResponseProto();
        lockResponseProto.setRequestId(message.getRequestId());
        lockResponseProto.setSuccess(success);
        lockResponseProto.setMessage(success ? "Lock request successful" : "Lock request failed");
        log.info("Lock response, response={}", lockResponseProto);
        lockResponsePublisher.offer(lockResponseProto);
    }
}
