package com.ganten.peanuts.aerondriver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AeronDriverProperties.class)
public class AeronDriverApplication {

    public static void main(String[] args) {
        SpringApplication.run(AeronDriverApplication.class, args);
    }
}
