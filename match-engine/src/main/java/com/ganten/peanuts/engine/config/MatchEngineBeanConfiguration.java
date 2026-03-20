package com.ganten.peanuts.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;

@Configuration
public class MatchEngineBeanConfiguration {

    @Bean(name = "executionReportAeronProperties")
    public AeronProperties executionReportAeronProperties() {
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setStreamId(Constants.AERON_STREAM_ID_EXECUTION_REPORT);
        aeronProperties.setChannel(Constants.AERON_CHANNEL);
        aeronProperties.setEnabled(Constants.AERON_ENABLED);
        aeronProperties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        aeronProperties.setDirectory(Constants.AERON_DIRECTORY);
        aeronProperties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        return aeronProperties;
    }

    @Bean(name = "orderBookAeronProperties")
    public AeronProperties orderBookAeronProperties() {
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setStreamId(Constants.AERON_STREAM_ID_ORDER_BOOK);
        aeronProperties.setChannel(Constants.AERON_CHANNEL);
        aeronProperties.setEnabled(Constants.AERON_ENABLED);
        aeronProperties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        aeronProperties.setDirectory(Constants.AERON_DIRECTORY);
        aeronProperties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        return aeronProperties;
    }

    @Bean(name = "tradeAeronProperties")
    public AeronProperties tradeAeronProperties() {
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setStreamId(Constants.AERON_STREAM_ID_TRADE);
        aeronProperties.setChannel(Constants.AERON_CHANNEL);
        aeronProperties.setEnabled(Constants.AERON_ENABLED);
        aeronProperties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        aeronProperties.setDirectory(Constants.AERON_DIRECTORY);
        aeronProperties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        return aeronProperties;
    }

    @Bean(name = "orderAeronProperties")
    public AeronProperties orderAeronProperties() {
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setStreamId(Constants.AERON_STREAM_ID_ORDER);
        aeronProperties.setChannel(Constants.AERON_CHANNEL);
        aeronProperties.setEnabled(Constants.AERON_ENABLED);
        aeronProperties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        aeronProperties.setDirectory(Constants.AERON_DIRECTORY);
        aeronProperties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        return aeronProperties;
    }
}
