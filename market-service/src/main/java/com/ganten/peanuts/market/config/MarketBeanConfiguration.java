package com.ganten.peanuts.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;

@Configuration
public class MarketBeanConfiguration {

    @Bean(name = "tradeAeronProperties")
    public AeronProperties tradeAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setStreamId(Constants.AERON_STREAM_ID_TRADE);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT_MARKET);
        return properties;
    }

    @Bean(name = "orderBookAeronProperties")
    public AeronProperties orderBookAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setStreamId(Constants.AERON_STREAM_ID_ORDER_BOOK);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT_MARKET);
        return properties;
    }
}
