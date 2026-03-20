package com.ganten.peanuts.account.messaging.subscriber;

import javax.annotation.PostConstruct;

import org.agrona.DirectBuffer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.ganten.peanuts.account.service.AccountService;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockRequestCodec;
import com.ganten.peanuts.protocol.model.LockRequestProto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LockAeronSubscriber extends AbstractAeronSubscriber<LockRequestProto, LockRequestCodec> {

    private final AccountService accountService;

    public LockAeronSubscriber(@Qualifier("lockResponseSubscriber") AeronProperties aeronProperties,
            AccountService accountService) {
        super(aeronProperties, LockRequestCodec.getInstance());
        this.accountService = accountService;
    }

    @Override
    protected void onMessage(LockRequestProto message) {
        boolean success = accountService.tryLock(message.getUserId(), message.getCurrency(), message.getAmount());
        if (!success) {
            log.warn("Lock request failed, userId={}, currency={}, amount={}", message.getUserId(), message.getCurrency(), message.getAmount());
        }
    }
}
