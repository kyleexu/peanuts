package com.ganten.peanuts.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.ganten.peanuts.engine.config.MatchEngineProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MatchEngineProperties.class)
public class MatchEngine {

    public static void main(String[] args) {
        SpringApplication.run(MatchEngine.class, args);
    }
}
