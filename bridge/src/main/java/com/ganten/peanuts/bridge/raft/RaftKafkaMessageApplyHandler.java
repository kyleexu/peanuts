package com.ganten.peanuts.bridge.raft;

import java.util.function.Function;

import org.springframework.kafka.core.KafkaTemplate;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganten.peanuts.protocol.raft.RaftMessageApplyHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Raft 状态机提交回调：将 committed 的业务消息写入 Kafka。
 *
 * <p>
 * 该 module 以“监听”为主，不负责向 raft 投递（不调用 Node#apply）。因此默认不阻塞 Raft 线程。
 * </p>
 */
@Slf4j
public class RaftKafkaMessageApplyHandler<T> implements RaftMessageApplyHandler<T> {

    private final int streamId;
    private final String topic;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Function<T, String> keyExtractor;

    public RaftKafkaMessageApplyHandler(
            int streamId,
            String topic,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Function<T, String> keyExtractor) {
        this.streamId = streamId;
        this.topic = topic;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.keyExtractor = keyExtractor;
    }

    @Override
    public void onCommitted(T message, boolean localApply, Closure done) {
        // 消息为空的时候，退出
        if (message == null) {
            if (localApply && done != null) {
                done.run(Status.OK());
            }
            return;
        }
        
        final String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize raft message to json, streamId={}, topic={}", streamId, topic, e);
            // 如果失败了，如果是本地节点，那么完成这个 raft 消息
            if (localApply && done != null) {
                done.run(Status.OK());
            }
            return;
        }

        // 发送 kafka 消息
        final String key = keyExtractor == null ? null : keyExtractor.apply(message);
        if (key == null) {
            kafkaTemplate.send(topic, json);
        } else {
            kafkaTemplate.send(topic, key, json);
        }

        if (localApply && done != null) {
            // listener_only 场景：localApply 通常为 false；这里仅保证不会阻塞 raft 执行线程。
            done.run(Status.OK());
        }
    }
}
