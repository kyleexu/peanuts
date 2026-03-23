package com.ganten.peanuts.bridge.raft;

import java.util.function.Function;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFutureCallback;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganten.peanuts.protocol.raft.RaftMessageApplyHandler;

/**
 * Raft 状态机提交回调：将 committed 的业务消息写入 Kafka。
 *
 * <p>该 module 以“监听”为主，不负责向 raft 投递（不调用 Node#apply）。因此默认不阻塞 Raft 线程。</p>
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class RaftKafkaMessageApplyHandler implements RaftMessageApplyHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(RaftKafkaMessageApplyHandler.class);

    private final int streamId;
    private final String topic;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Function<Object, String> keyExtractor;

    public RaftKafkaMessageApplyHandler(
            int streamId,
            String topic,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Function<Object, String> keyExtractor) {
        this.streamId = streamId;
        this.topic = topic;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.keyExtractor = keyExtractor;
    }

    @Override
    public void onCommitted(Object message, boolean localApply, Closure done) {
        if (message == null) {
            if (localApply && done != null) {
                done.run(Status.OK());
            }
            return;
        }

        final String key = keyExtractor == null ? null : keyExtractor.apply(message);
        final String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize raft message to json, streamId={}, topic={}", streamId, topic, e);
            if (localApply && done != null) {
                done.run(Status.OK());
            }
            return;
        }

        try {
            if (key == null) {
                kafkaTemplate.send(topic, json).addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
                    @Override
                    public void onFailure(Throwable ex) {
                        log.error("Kafka send failed, streamId={}, topic={}", streamId, topic, ex);
                    }

                    @Override
                    public void onSuccess(SendResult<String, String> result) {
                        // fire-and-forget
                    }
                });
            } else {
                kafkaTemplate.send(topic, key, json).addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
                    @Override
                    public void onFailure(Throwable ex) {
                        log.error("Kafka send failed, streamId={}, topic={}", streamId, topic, ex);
                    }

                    @Override
                    public void onSuccess(SendResult<String, String> result) {
                        // fire-and-forget
                    }
                });
            }
        } catch (Throwable t) {
            log.error("Kafka send throw, streamId={}, topic={}", streamId, topic, t);
        }

        if (localApply && done != null) {
            // listener_only 场景：localApply 通常为 false；这里仅保证不会阻塞 raft 执行线程。
            done.run(Status.OK());
        }
    }

    // kept for possible future customization (e.g. localApply should succeed only when kafka send success)
    public interface KafkaSendErrorHandler {
        void onSendError(int streamId, String topic, Throwable ex);
    }
}

