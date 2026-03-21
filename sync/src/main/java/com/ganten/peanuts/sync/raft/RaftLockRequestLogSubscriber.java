package com.ganten.peanuts.sync.raft;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockRequestCodec;
import com.ganten.peanuts.protocol.model.LockRequestProto;

import lombok.extern.slf4j.Slf4j;

/**
 * order → account 资金锁定请求（stream 2101）。
 */
@Slf4j
@Component
public class RaftLockRequestLogSubscriber extends AbstractAeronSubscriber<LockRequestProto, LockRequestCodec> {

    public RaftLockRequestLogSubscriber(
            @Qualifier("syncLockRequestAeronProperties") AeronProperties aeronProperties) {
        super(aeronProperties, LockRequestCodec.getInstance());
    }

    @Override
    protected void onMessage(LockRequestProto message) {
        log.info("[raft] role=order stream={} entry=LockRequest requestId={} userId={} currency={} amount={} ts={}",
                Constants.AERON_STREAM_ID_LOCK_REQUEST,
                message.getRequestId(),
                message.getUserId(),
                message.getCurrency(),
                message.getAmount(),
                message.getTimestamp());
    }
}
