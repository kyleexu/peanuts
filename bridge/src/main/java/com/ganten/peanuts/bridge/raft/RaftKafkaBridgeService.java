package com.ganten.peanuts.bridge.raft;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganten.peanuts.bridge.codec.CodecFactory;
import com.ganten.peanuts.bridge.codec.CodecFactory.CodecSpec;
import com.ganten.peanuts.bridge.config.RaftKafkaBridgeProperties;
import com.ganten.peanuts.common.entity.RaftProperties;
import com.ganten.peanuts.protocol.raft.CodecRaftStateMachine;
import com.ganten.peanuts.protocol.raft.RaftBootstrap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RaftKafkaBridgeService {

    private final RaftKafkaBridgeProperties properties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final Map<Integer, RaftBootstrap> bootstraps = new ConcurrentHashMap<Integer, RaftBootstrap>();

    public RaftKafkaBridgeService(RaftKafkaBridgeProperties properties,
                                    KafkaTemplate<String, String> kafkaTemplate,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        if (properties.getStreams() == null || properties.getStreams().isEmpty()) {
            log.warn("No raft streams configured, nothing to bridge.");
            return;
        }

        for (RaftKafkaBridgeProperties.StreamConfig s : properties.getStreams()) {
            int streamId = s.getStreamId();
            String topic = s.getTopic();
            CodecSpec<?> spec = CodecFactory.specForStreamId(streamId);

            RaftProperties rp = new RaftProperties();
            rp.setDataPath(s.getRaft().getDataPath());
            rp.setGroupId(s.getRaft().getGroupId());
            rp.setServerId(s.getRaft().getServerId());
            rp.setInitConf(s.getRaft().getInitConf());
            // listener_only: 不需要通过 raftApplyMode 控制业务 onMessage（本 module 只写 Kafka）。

            RaftKafkaMessageApplyHandler<?> applyHandler = new RaftKafkaMessageApplyHandler<>(
                    streamId,
                    topic,
                    kafkaTemplate,
                    objectMapper,
                    spec.getKeyExtractor());

            @SuppressWarnings({ "rawtypes", "unchecked" })
            CodecRaftStateMachine stateMachine = new CodecRaftStateMachine(spec.getCodec(), applyHandler);

            RaftBootstrap bootstrap = new RaftBootstrap(rp, stateMachine);
            try {
                bootstrap.start();
                bootstraps.put(streamId, bootstrap);
                log.info("Raft->Kafka bridge started, streamId={}, topic={}", streamId, topic);
            } catch (IOException e) {
                log.error("Failed to start raft bootstrap, streamId={}, topic={}", streamId, topic, e);
                // Keep going for other streams
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        for (RaftBootstrap bootstrap : bootstraps.values()) {
            try {
                bootstrap.shutdown();
            } catch (Throwable t) {
                log.warn("Error while shutting down raft bootstrap", t);
            }
        }
        bootstraps.clear();
    }
}

