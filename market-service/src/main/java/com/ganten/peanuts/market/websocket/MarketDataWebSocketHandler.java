package com.ganten.peanuts.market.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * WebSocket 连接处理器，管理客户端连接和消息广播
 */
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataWebSocketHandler.class);

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        logger.info("WebSocket connected, client: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        logger.info("WebSocket disconnected, client: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.debug("Received message from client {}: {}", session.getId(), payload);
        // 可在此处理客户端消息，例如订阅特定合约
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket error for client {}: {}", session.getId(), exception.getMessage(), exception);
    }

    /**
     * 广播市场数据消息到所有连接的客户端
     *
     * @param message 市场数据消息
     */
    public void broadcast(Object message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            logger.error("Failed to serialize message", e);
            return;
        }

        TextMessage textMessage = new TextMessage(payload);
        List<String> failedSessions = new ArrayList<>();

        for (WebSocketSession session : sessions.values()) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                logger.warn("Failed to send message to client {}: {}", session.getId(), e.getMessage());
                failedSessions.add(session.getId());
            }
        }

        // 清理失败的连接
        failedSessions.forEach(sessions::remove);
    }

    /**
     * 获取当前连接数
     *
     * @return 连接数
     */
    public int getConnectionCount() {
        return sessions.size();
    }
}
