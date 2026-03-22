package com.ganten.peanuts.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.aeron.AeronSubscriberPropertiesFactory;

@Configuration
public class MarketBeanConfiguration {

    @Bean(name = "tradeAeronProperties")
    public AeronProperties tradeAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_TRADE,
                Constants.AERON_FRAGMENT_LIMIT_MARKET,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
    }

    @Bean(name = "orderBookAeronProperties")
    public AeronProperties orderBookAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_ORDER_BOOK,
                Constants.AERON_FRAGMENT_LIMIT_MARKET,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
    }
}
