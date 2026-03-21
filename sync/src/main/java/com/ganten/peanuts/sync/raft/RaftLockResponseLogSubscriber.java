package com.ganten.peanuts.sync.raft;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.sync.kafka.RaftLogDelivery;
import com.ganten.peanuts.sync.kafka.RaftLogKafkaBridge;
import com.ganten.peanuts.protocol.aeron.AbstractAeronSubscriber;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.codec.LockResponseCodec;
import com.ganten.peanuts.protocol.model.LockResponseProto;

import lombok.extern.slf4j.Slf4j;

/**
 * account → order 资金锁定响应（stream 2102）。
 */
@Slf4j
@Component
public class RaftLockResponseLogSubscriber extends AbstractAeronSubscriber<LockResponseProto, LockResponseCodec> {

    private final ObjectProvider<RaftLogKafkaBridge> kafkaBridge;

    public RaftLockResponseLogSubscriber(
            @Qualifier("syncLockResponseAeronProperties") AeronProperties aeronProperties,
            ObjectProvider<RaftLogKafkaBridge> kafkaBridge) {
        super(aeronProperties, LockResponseCodec.getInstance());
        this.kafkaBridge = kafkaBridge;
    }

    @Override
    protected void onMessage(LockResponseProto message) {
        log.info("[raft] role=account stream={} entry=LockResponse requestId={} success={} message={}",
                Constants.AERON_STREAM_ID_LOCK_RESPONSE,
                message.getRequestId(),
                message.isSuccess(),
                message.getMessage());
        RaftLogDelivery.maybePublish(kafkaBridge, "account", Constants.AERON_STREAM_ID_LOCK_RESPONSE, "LockResponse", message);
    }
}
