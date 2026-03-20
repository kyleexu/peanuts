package com.ganten.peanuts.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ganten.peanuts.protocol.aeron.AeronProperties;
import io.aeron.CommonContext;

@Configuration
public class BeanConfiguration {

    @Bean(name = "tradeAeronProperties")
    public AeronProperties tradeAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setEnabled(true);
        properties.setChannel("aeron:ipc");
        properties.setStreamId(2003);
        properties.setLaunchEmbeddedDriver(true);
        properties.setDirectory(CommonContext.getAeronDirectoryName());
        properties.setFragmentLimit(100);
        return properties;
    }

    @Bean(name = "orderBookAeronProperties")
    public AeronProperties orderBookAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setEnabled(true);
        properties.setChannel("aeron:ipc");
        properties.setStreamId(2004);
        properties.setLaunchEmbeddedDriver(true);
        properties.setDirectory(CommonContext.getAeronDirectoryName());
        properties.setFragmentLimit(100);
        return properties;
    }
}
