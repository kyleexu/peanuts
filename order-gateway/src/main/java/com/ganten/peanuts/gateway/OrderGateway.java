package com.ganten.peanuts.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.ganten.peanuts.gateway.config.AeronProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AeronProperties.class)
public class OrderGateway {

    public static void main(String[] args) {
        SpringApplication.run(OrderGateway.class, args);
    }
}
