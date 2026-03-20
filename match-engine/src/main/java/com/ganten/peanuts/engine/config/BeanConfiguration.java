package com.ganten.peanuts.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ganten.peanuts.protocol.aeron.AeronProperties;

import io.aeron.CommonContext;

@Configuration
public class BeanConfiguration {

    @Bean
    public AeronProperties executionReportAeronProperties() {
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setStreamId(2002);
        aeronProperties.setChannel("aeron:ipc");
        aeronProperties.setEnabled(true);
        aeronProperties.setLaunchEmbeddedDriver(true);
        aeronProperties.setDirectory(CommonContext.getAeronDirectoryName());
        aeronProperties.setFragmentLimit(10);
        return aeronProperties;
    }

    @Bean
    public AeronProperties orderBookAeronProperties() {
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setStreamId(2004);
        aeronProperties.setChannel("aeron:ipc");
        aeronProperties.setEnabled(true);
        aeronProperties.setLaunchEmbeddedDriver(true);
        aeronProperties.setDirectory(CommonContext.getAeronDirectoryName());
        aeronProperties.setFragmentLimit(10);
        return aeronProperties;
    }

    @Bean
    public AeronProperties tradeAeronProperties() {
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setStreamId(2003);
        aeronProperties.setChannel("aeron:ipc");
        aeronProperties.setEnabled(true);
        aeronProperties.setLaunchEmbeddedDriver(true);
        aeronProperties.setDirectory(CommonContext.getAeronDirectoryName());
        aeronProperties.setFragmentLimit(10);
        return aeronProperties;
    }

    @Bean
    public AeronProperties orderAeronProperties() {
        AeronProperties aeronProperties = new AeronProperties();
        aeronProperties.setStreamId(2001);
        aeronProperties.setChannel("aeron:ipc");
        aeronProperties.setEnabled(true);
        aeronProperties.setLaunchEmbeddedDriver(true);
        aeronProperties.setDirectory(CommonContext.getAeronDirectoryName());
        aeronProperties.setFragmentLimit(10);
        return aeronProperties;
    }
}
