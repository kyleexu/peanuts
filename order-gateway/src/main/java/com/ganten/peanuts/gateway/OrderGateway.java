package com.ganten.peanuts.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.ganten.peanuts.gateway", "com.ganten.peanuts.protocol"})
@EnableScheduling
public class OrderGateway {

    public static void main(String[] args) {
        SpringApplication.run(OrderGateway.class, args);
    }
}
