package com.ganten.peanuts.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;

@Configuration
public class OrderBeanConfiguration {

    /**
     * 订单分发的AeronProperties
     * 
     * @return
     */
    @Bean(name = "orderDispatchAeronProperties")
    public AeronProperties orderDispatchAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(Constants.AERON_STREAM_ID_ORDER);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
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
        properties.setStreamId(Constants.AERON_STREAM_ID_LOCK_REQUEST);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
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
        properties.setStreamId(Constants.AERON_STREAM_ID_LOCK_RESPONSE);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT_LOCK_RESPONSE);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        return properties;
    }

    /**
     * 执行回报（match → order），与 README 中 stream 2002 一致。
     */
    @Bean(name = "executionReportAeronProperties")
    public AeronProperties executionReportAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(Constants.AERON_STREAM_ID_EXECUTION_REPORT);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
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
