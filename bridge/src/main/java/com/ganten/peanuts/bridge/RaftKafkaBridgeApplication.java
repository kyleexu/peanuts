package com.ganten.peanuts.bridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.ganten.peanuts.bridge",
        "com.ganten.peanuts.protocol"
})
public class RaftKafkaBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaftKafkaBridgeApplication.class, args);
    }
}

