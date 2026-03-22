package com.ganten.peanuts.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.aeron.AeronSubscriberPropertiesFactory;

@Configuration
public class OrderBeanConfiguration {

    @Bean(name = "orderDispatchAeronProperties")
    public AeronProperties orderDispatchAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_ORDER,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
    }

    @Bean(name = "accountLockAeronRequestProperties")
    public AeronProperties accountLockAeronRequestProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_LOCK_REQUEST,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
    }

    @Bean(name = "accountLockAeronResponseProperties")
    public AeronProperties accountLockAeronResponseProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_LOCK_RESPONSE,
                Constants.AERON_FRAGMENT_LIMIT_LOCK_RESPONSE,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
    }

    @Bean(name = "executionReportAeronProperties")
    public AeronProperties executionReportAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_EXECUTION_REPORT,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
    }

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
