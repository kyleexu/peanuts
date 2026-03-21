package com.ganten.peanuts.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ganten.peanuts.market.websocket.MarketDataWebSocketHandler;

/**
 * WebSocket Handler 配置类
 */
@Configuration
public class WebSocketHandlerConfig {

    @Bean
    public MarketDataWebSocketHandler marketDataWebSocketHandler() {
        return new MarketDataWebSocketHandler();
    }
}
