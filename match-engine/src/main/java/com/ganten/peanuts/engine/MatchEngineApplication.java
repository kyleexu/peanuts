package com.ganten.peanuts.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.ganten.peanuts.engine", "com.ganten.peanuts.protocol"})
@EnableScheduling
public class MatchEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchEngineApplication.class, args);
    }
}
