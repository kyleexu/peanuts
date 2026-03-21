package com.ganten.peanuts.market.websocket;

import org.springframework.stereotype.Component;

/**
 * WebSocket 广播器，用于推送市场数据到客户端
 */
@Component
public class WebSocketBroadcaster {

    private final MarketDataWebSocketHandler handler;

    public WebSocketBroadcaster(MarketDataWebSocketHandler handler) {
        this.handler = handler;
    }

    /**
     * 广播市场数据消息
     *
     * @param message 消息对象
     */
    public void send(Object message) {
        if (handler.getConnectionCount() > 0) {
            handler.broadcast(message);
        }
    }
}
