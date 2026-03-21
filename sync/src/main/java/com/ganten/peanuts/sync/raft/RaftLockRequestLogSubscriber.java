package com.ganten.peanuts.sync.raft;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.sync.kafka.RaftLogDelivery;
import com.ganten.peanuts.sync.kafka.RaftLogKafkaBridge;
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

    private final ObjectProvider<RaftLogKafkaBridge> kafkaBridge;

    public RaftLockRequestLogSubscriber(
            @Qualifier("syncLockRequestAeronProperties") AeronProperties aeronProperties,
            ObjectProvider<RaftLogKafkaBridge> kafkaBridge) {
        super(aeronProperties, LockRequestCodec.getInstance());
        this.kafkaBridge = kafkaBridge;
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
        RaftLogDelivery.maybePublish(kafkaBridge, "order", Constants.AERON_STREAM_ID_LOCK_REQUEST, "LockRequest", message);
    }
}
