package com.ganten.peanuts.sync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;

/**
 * 与 order / account / match / market 链路一致的 Aeron 参数，供仅打印 Raft 日志的订阅端使用。
 */
@Configuration
public class SyncAeronConfiguration {

    @Bean(name = "syncLockRequestAeronProperties")
    public AeronProperties syncLockRequestAeronProperties() {
        return base(Constants.AERON_STREAM_ID_LOCK_REQUEST, Constants.AERON_FRAGMENT_LIMIT);
    }

    @Bean(name = "syncLockResponseAeronProperties")
    public AeronProperties syncLockResponseAeronProperties() {
        return base(Constants.AERON_STREAM_ID_LOCK_RESPONSE, Constants.AERON_FRAGMENT_LIMIT_LOCK_RESPONSE);
    }

    @Bean(name = "syncOrderCommandAeronProperties")
    public AeronProperties syncOrderCommandAeronProperties() {
        return base(Constants.AERON_STREAM_ID_ORDER, Constants.AERON_FRAGMENT_LIMIT);
    }

    @Bean(name = "syncExecutionReportAeronProperties")
    public AeronProperties syncExecutionReportAeronProperties() {
        return base(Constants.AERON_STREAM_ID_EXECUTION_REPORT, Constants.AERON_FRAGMENT_LIMIT);
    }

    @Bean(name = "syncTradeAeronProperties")
    public AeronProperties syncTradeAeronProperties() {
        return base(Constants.AERON_STREAM_ID_TRADE, Constants.AERON_FRAGMENT_LIMIT_MARKET);
    }

    @Bean(name = "syncOrderBookAeronProperties")
    public AeronProperties syncOrderBookAeronProperties() {
        return base(Constants.AERON_STREAM_ID_ORDER_BOOK, Constants.AERON_FRAGMENT_LIMIT_MARKET);
    }

    private static AeronProperties base(int streamId, int fragmentLimit) {
        AeronProperties properties = new AeronProperties();
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setStreamId(streamId);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        properties.setFragmentLimit(fragmentLimit);
        return properties;
    }
}
