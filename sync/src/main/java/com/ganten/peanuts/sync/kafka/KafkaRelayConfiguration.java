package com.ganten.peanuts.sync.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * 仅在 {@code peanuts.kafka.enabled=true} 时启用：与 Spring Boot 默认 Kafka 自动配置分离，避免未启用时仍要求 broker。
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "peanuts.kafka", name = "enabled", havingValue = "true")
public class KafkaRelayConfiguration {

    @Value("${peanuts.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${peanuts.kafka.consumer-group}")
    private String consumerGroup;

    @Bean
    public ProducerFactory<String, RaftLogEnvelope> raftLogProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, RaftLogEnvelope> raftLogKafkaTemplate() {
        return new KafkaTemplate<>(raftLogProducerFactory());
    }

    @Bean
    public ConsumerFactory<String, RaftLogEnvelope> raftLogConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        JsonDeserializer<RaftLogEnvelope> valueDeserializer = new JsonDeserializer<>(RaftLogEnvelope.class);
        valueDeserializer.addTrustedPackages("com.ganten.peanuts.sync.kafka");
        valueDeserializer.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RaftLogEnvelope> raftLogKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, RaftLogEnvelope> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(raftLogConsumerFactory());
        return factory;
    }
}
