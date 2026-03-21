package com.ganten.peanuts.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;

/**
 * 同步/回收服务入口：订阅 Aeron 业务流并打印；可选将事件中继到 Kafka（见 peanuts.kafka.enabled）。
 */
@SpringBootApplication(exclude = KafkaAutoConfiguration.class)
public class SyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncApplication.class, args);
    }
}
