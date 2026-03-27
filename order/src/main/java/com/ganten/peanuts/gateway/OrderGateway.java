package com.ganten.peanuts.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.ganten.peanuts.gateway", "com.ganten.peanuts.protocol"})
public class OrderGateway {

    public static void main(String[] args) {
        SpringApplication.run(OrderGateway.class, args);
    }
}
