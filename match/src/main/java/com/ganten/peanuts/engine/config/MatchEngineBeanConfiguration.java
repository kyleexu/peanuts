package com.ganten.peanuts.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.aeron.AeronSubscriberPropertiesFactory;

@Configuration
public class MatchEngineBeanConfiguration {

    @Bean(name = "executionReportAeronProperties")
    public AeronProperties executionReportAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_EXECUTION_REPORT,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER_MATCH_ENGINE);
    }

    @Bean(name = "orderBookAeronProperties")
    public AeronProperties orderBookAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_ORDER_BOOK,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER_MATCH_ENGINE);
    }

    @Bean(name = "tradeAeronProperties")
    public AeronProperties tradeAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_TRADE,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER_MATCH_ENGINE);
    }

    @Bean(name = "orderAeronProperties")
    public AeronProperties orderAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_ORDER,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER_MATCH_ENGINE);
    }
}
