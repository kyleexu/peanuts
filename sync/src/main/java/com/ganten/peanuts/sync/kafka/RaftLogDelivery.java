package com.ganten.peanuts.sync.kafka;

import org.springframework.beans.factory.ObjectProvider;

/**
 * 可选 Kafka 中继：未启用 {@code peanuts.kafka.enabled} 时不发送。
 */
public final class RaftLogDelivery {

    private RaftLogDelivery() {
    }

    public static void maybePublish(
            ObjectProvider<RaftLogKafkaBridge> kafkaBridge,
            String role,
            int streamId,
            String entryType,
            Object payloadPojo) {
        RaftLogKafkaBridge bridge = kafkaBridge.getIfAvailable();
        if (bridge != null) {
            bridge.publish(role, streamId, entryType, payloadPojo);
        }
    }
}
