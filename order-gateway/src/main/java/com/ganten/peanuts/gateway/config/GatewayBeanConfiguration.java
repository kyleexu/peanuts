package com.ganten.peanuts.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.ganten.peanuts.protocol.aeron.AeronProperties;

@Configuration
public class GatewayBeanConfiguration {

    /**
     * 订单分发的AeronProperties
     * 
     * @return
     */
    @Bean(name = "orderDispatchAeronProperties")
    public AeronProperties orderDispatchAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(2001);
        properties.setChannel("aeron:ipc");
        properties.setEnabled(true);
        properties.setFragmentLimit(10);
        return properties;
    }

    /**
     * 加锁请求的AeronProperties
     * 
     * @return
     */
    @Bean(name = "accountLockAeronRequestProperties")
    public AeronProperties accountLockAeronRequestProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(2101);
        properties.setChannel("aeron:ipc");
        properties.setEnabled(true);
        properties.setFragmentLimit(10);
        return properties;
    }

    /**
     * 加锁响应的AeronProperties
     * 
     * @return
     */
    @Bean(name = "accountLockAeronResponseProperties")
    public AeronProperties accountLockAeronResponseProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(2102);
        properties.setChannel("aeron:ipc");
        properties.setEnabled(true);
        properties.setFragmentLimit(20);
        return properties;
    }

    /**
     * 订单分发的线程池
     * 
     * @return
     */
    @Bean(name = "orderDispatchExecutor")
    public TaskExecutor orderDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("order-dispatch-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2000);
        executor.initialize();
        return executor;
    }
}
