package com.ganten.peanuts.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 同步/回收服务入口：当前仅订阅各业务 Aeron 流并打印（视为对接各模块的复制日志）。
 */
@SpringBootApplication
public class SyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncApplication.class, args);
    }
}
