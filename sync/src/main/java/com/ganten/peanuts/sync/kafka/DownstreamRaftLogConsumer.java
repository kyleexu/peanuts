package com.ganten.peanuts.sync.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 占位下游：模拟「消费 Kafka 中的复制日志并更新 DB」。此处仅打日志，真实落库可替换为 JDBC/MyBatis。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "peanuts.kafka", name = "enabled", havingValue = "true")
public class DownstreamRaftLogConsumer {

    @KafkaListener(
            topics = "${peanuts.kafka.topic}",
            containerFactory = "raftLogKafkaListenerContainerFactory")
    public void onRaftLog(RaftLogEnvelope envelope) {
        log.info("[downstream-db] apply role={} stream={} type={} at={} payloadKeys={}",
                envelope.getRole(),
                envelope.getStreamId(),
                envelope.getEntryType(),
                envelope.getEmittedAtMs(),
                envelope.getPayload() != null ? envelope.getPayload().keySet() : null);
    }
}
