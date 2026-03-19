package com.ganten.peanuts.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.ganten.peanuts.market.config.MarketAeronProperties;

@SpringBootApplication
@EnableConfigurationProperties(MarketAeronProperties.class)
public class MarketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }
}
