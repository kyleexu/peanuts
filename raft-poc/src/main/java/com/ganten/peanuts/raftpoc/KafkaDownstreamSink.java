package com.ganten.peanuts.raftpoc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 将已提交日志发送到 Kafka（单分区 key 保证顺序时需由上游约定 key；此处演示用 index 作 key）。
 */
public final class KafkaDownstreamSink implements DownstreamSink {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaDownstreamSink.class);

    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ObjectMapper mapper = new ObjectMapper();

    public KafkaDownstreamSink(String bootstrapServers, String topic) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        this.producer = new KafkaProducer<>(props);
        this.topic = topic;
    }

    @Override
    public void onCommitted(long logIndex, long logTerm, byte[] command) {
        Map<String, Object> body = new HashMap<>();
        body.put("logIndex", logIndex);
        body.put("logTerm", logTerm);
        body.put("payloadUtf8", command == null ? "" : new String(command, StandardCharsets.UTF_8));
        try {
            String json = mapper.writeValueAsString(body);
            producer.send(new ProducerRecord<>(topic, String.valueOf(logIndex), json), (meta, ex) -> {
                if (ex != null) {
                    LOG.warn("Kafka send failed: {}", ex.toString());
                }
            });
        } catch (JsonProcessingException e) {
            LOG.warn("JSON encode failed: {}", e.toString());
        }
    }

    public void close() {
        producer.close();
    }
}
