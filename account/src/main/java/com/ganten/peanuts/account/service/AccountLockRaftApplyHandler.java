package com.ganten.peanuts.account.service;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.ganten.peanuts.account.messaging.publisher.LockResponsePublisher;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import com.ganten.peanuts.protocol.model.LockResponseProto;
import com.ganten.peanuts.protocol.raft.RaftMessageApplyHandler;

import org.springframework.stereotype.Component;

/**
 * 资金锁命令在 Raft 提交后的业务应用器。
 */
@Component
public class AccountLockRaftApplyHandler implements RaftMessageApplyHandler<LockRequestProto> {

    private final AccountService accountService;
    private final LockResponsePublisher lockResponsePublisher;

    public AccountLockRaftApplyHandler(AccountService accountService, LockResponsePublisher lockResponsePublisher) {
        this.accountService = accountService;
        this.lockResponsePublisher = lockResponsePublisher;
    }

    @Override
    public void onCommitted(LockRequestProto message, boolean localApply, Closure done) {
        boolean success = accountService.tryLock(message.getUserId(), message.getCurrency(), message.getAmount());
        if (localApply) {
            LockResponseProto response = new LockResponseProto();
            response.setRequestId(message.getRequestId());
            response.setSuccess(success);
            response.setMessage(success ? "Lock request successful" : "Lock request failed");
            lockResponsePublisher.offer(response);
            if (done != null) {
                done.run(Status.OK());
            }
        }
    }
}
