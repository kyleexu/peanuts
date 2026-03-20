package com.ganten.peanuts.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import io.aeron.CommonContext;

import com.ganten.peanuts.protocol.aeron.AeronProperties;

@Configuration
public class BeanConfiguration {

    @Bean
    @Qualifier("lockResponseAeronProperties")
    public AeronProperties lockResponseAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(2102);
        properties.setChannel("aeron:ipc");
        properties.setEnabled(true);
        properties.setLaunchEmbeddedDriver(true);
        properties.setDirectory(CommonContext.getAeronDirectoryName());
        properties.setFragmentLimit(10);
        return properties;
    }

    @Bean
    @Qualifier("lockRequestAeronProperties")
    public AeronProperties lockRequestAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(2101);
        properties.setChannel("aeron:ipc");
        properties.setEnabled(true);
        properties.setLaunchEmbeddedDriver(true);
        properties.setDirectory(CommonContext.getAeronDirectoryName());
        properties.setFragmentLimit(10);
        return properties;
    }


    @Bean
    @Qualifier("tradeAeronProperties")
    public AeronProperties tradeAeronProperties() {
        AeronProperties properties = new AeronProperties();
        properties.setStreamId(2103);
        properties.setChannel("aeron:ipc");
        properties.setEnabled(true);
        properties.setLaunchEmbeddedDriver(true);
        properties.setDirectory(CommonContext.getAeronDirectoryName());
        properties.setFragmentLimit(10);
        return properties;
    }
}
