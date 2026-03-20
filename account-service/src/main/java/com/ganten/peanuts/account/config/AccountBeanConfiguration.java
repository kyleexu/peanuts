package com.ganten.peanuts.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

import com.ganten.peanuts.common.constant.Constants;
import com.ganten.peanuts.protocol.aeron.AeronProperties;

@Configuration
public class AccountBeanConfiguration {

    @Bean(name = "lockResponseAeronProperties")
    public AeronProperties lockResponseAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(Constants.AERON_STREAM_ID_LOCK_RESPONSE);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        return properties;
    }

    @Bean(name = "lockRequestAeronProperties")
    public AeronProperties lockRequestAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(Constants.AERON_STREAM_ID_LOCK_REQUEST);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        return properties;
    }


    @Bean(name = "tradeAeronProperties")
    public AeronProperties tradeAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(Constants.AERON_STREAM_ID_ACCOUNT_TRADE);
        properties.setChannel(Constants.AERON_CHANNEL);
        properties.setEnabled(Constants.AERON_ENABLED);
        properties.setLaunchEmbeddedDriver(Constants.AERON_LAUNCH_EMBEDDED_DRIVER);
        properties.setDirectory(Constants.AERON_DIRECTORY);
        properties.setFragmentLimit(Constants.AERON_FRAGMENT_LIMIT);
        return properties;
    }
}
