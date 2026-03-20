package com.ganten.peanuts.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.ganten.peanuts.engine.config.MatchEngineProperties;

@SpringBootApplication(scanBasePackages = {"com.ganten.peanuts.engine", "com.ganten.peanuts.protocol"})
@EnableScheduling
@EnableConfigurationProperties(MatchEngineProperties.class)
public class MatchEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchEngineApplication.class, args);
    }
}
