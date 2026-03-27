package com.ganten.peanuts.maker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MakerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MakerApplication.class, args);
    }
}
