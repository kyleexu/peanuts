package com.ganten.peanuts.sync.kafka;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 将 Aeron 上已解码的业务对象封装为 {@link RaftLogEnvelope} 发往 Kafka（占位：未来可改为 tail Raft 日志）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "peanuts.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RaftLogKafkaBridge {

    private final KafkaTemplate<String, RaftLogEnvelope> raftLogKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${peanuts.kafka.topic}")
    private String topic;

    public void publish(String role, int streamId, String entryType, Object payloadPojo) {
        Map<String, Object> payload = objectMapper.convertValue(payloadPojo, new TypeReference<Map<String, Object>>() {});
        RaftLogEnvelope envelope = RaftLogEnvelope.builder()
                .role(role)
                .streamId(streamId)
                .entryType(entryType)
                .emittedAtMs(System.currentTimeMillis())
                .payload(payload)
                .build();
        String key = role + ":" + streamId;
        raftLogKafkaTemplate.send(topic, key, envelope).addCallback(
                (SendResult<String, RaftLogEnvelope> r) -> {
                    if (log.isTraceEnabled() && r != null) {
                        log.trace("Kafka send ok partition={} offset={}",
                                r.getRecordMetadata().partition(),
                                r.getRecordMetadata().offset());
                    }
                },
                ex -> log.warn("Kafka send failed: {}", ex.toString()));
    }
}
