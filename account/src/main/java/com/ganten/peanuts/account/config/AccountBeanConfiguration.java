package com.ganten.peanuts.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;
import com.ganten.peanuts.protocol.aeron.AeronSubscriberPropertiesFactory;

@Configuration
public class AccountBeanConfiguration {

    @Bean(name = "lockResponseAeronProperties")
    public AeronProperties lockResponseAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_LOCK_RESPONSE,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
    }

    @Bean(name = "lockRequestAeronProperties")
    public AeronProperties lockRequestAeronProperties() {
        return AeronSubscriberPropertiesFactory.accountLockRequestSubscriber();
    }

    @Bean(name = "tradeAeronProperties")
    public AeronProperties tradeAeronProperties() {
        return AeronSubscriberPropertiesFactory.standardSubscriber(
                Constants.AERON_STREAM_ID_TRADE,
                Constants.AERON_FRAGMENT_LIMIT,
                Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
    }
}
