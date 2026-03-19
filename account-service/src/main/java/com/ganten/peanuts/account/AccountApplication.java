package com.ganten.peanuts.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.ganten.peanuts.account.config.AccountAeronProperties;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(AccountAeronProperties.class)
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
