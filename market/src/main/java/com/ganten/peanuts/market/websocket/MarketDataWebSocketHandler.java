package com.ganten.peanuts.market.websocket;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganten.peanuts.market.model.CandleSnapshot;
import com.ganten.peanuts.market.model.MarketMessage;
import com.ganten.peanuts.market.model.OrderBookSnapshot;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 连接处理器，管理客户端连接和消息广播
 */
@Slf4j
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private static final String TOPIC_TICKER = "ticker";
    private static final String TOPIC_CANDLE = "candle";
    private static final String TOPIC_ORDERBOOK = "orderbook";
    private static final String TOPIC_TRADE = "trade";

    private static final String OP_SUBSCRIBE = "subscribe";
    private static final String OP_UNSUBSCRIBE = "unsubscribe";

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> sessionTopics = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        sessionTopics.put(session.getId(), defaultTopics());
        log.info("WebSocket connected, client: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        sessionTopics.remove(session.getId());
        log.info("WebSocket disconnected, client: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("Invalid client message from {}: {}", session.getId(), e.getMessage());
            return;
        }

        String op = normalize(root.path("op").asText(null));
        if (!StringUtils.hasText(op)) {
            op = normalize(root.path("action").asText(null));
        }

        Set<String> topics = parseTopics(root);
        if (!StringUtils.hasText(op) || topics.isEmpty()) {
            log.debug("Ignore client message from {}, op: {}, topics: {}", session.getId(), op, topics);
            return;
        }

        if (OP_SUBSCRIBE.equals(op)) {
            subscribe(session.getId(), topics);
        } else if (OP_UNSUBSCRIBE.equals(op)) {
            unsubscribe(session.getId(), topics);
        } else {
            log.debug("Unknown websocket op from {}, op: {}", session.getId(), op);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket error for client {}: {}", session.getId(), exception.getMessage(), exception);
    }

    /**
     * 广播市场数据消息到所有连接的客户端
     *
     * @param message 市场数据消息
     */
    public void broadcast(Object message) {
        Set<String> messageTopics = resolveMessageTopics(message);
        if (messageTopics.isEmpty()) {
            log.debug("Skip broadcast because message topic is unresolved");
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize message", e);
            return;
        }

        TextMessage textMessage = new TextMessage(payload);
        List<String> failedSessions = new ArrayList<>();

        for (WebSocketSession session : sessions.values()) {
            try {
                if (session.isOpen() && isSessionSubscribed(session.getId(), messageTopics)) {
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                log.warn("Failed to send message to client {}: {}", session.getId(), e.getMessage());
                failedSessions.add(session.getId());
            }
        }

        // 清理失败的连接
        failedSessions.forEach(this::removeSession);
    }

    /**
     * 获取当前连接数
     *
     * @return 连接数
     */
    public int getConnectionCount() {
        return sessions.size();
    }

    private Set<String> defaultTopics() {
        Set<String> defaults = new HashSet<>();
        defaults.add(TOPIC_TICKER);
        defaults.add(TOPIC_CANDLE);
        defaults.add(TOPIC_ORDERBOOK);
        defaults.add(TOPIC_TRADE);
        return Collections.synchronizedSet(defaults);
    }

    private Set<String> parseTopics(JsonNode root) {
        Set<String> topics = new HashSet<>();

        if (root.has("topic")) {
            addTopic(topics, root.path("topic").asText(null));
        }

        JsonNode topicsNode = root.path("topics");
        if (topicsNode.isArray()) {
            for (JsonNode topicNode : topicsNode) {
                addTopic(topics, topicNode.asText(null));
            }
        }

        return topics;
    }

    private void addTopic(Set<String> topics, String topic) {
        String normalized = normalize(topic);
        if (StringUtils.hasText(normalized)) {
            topics.add(normalized);
        }
    }

    private void subscribe(String sessionId, Set<String> topics) {
        Set<String> subscriptions =
                sessionTopics.computeIfAbsent(sessionId, key -> Collections.synchronizedSet(new HashSet<>()));
        subscriptions.addAll(topics);
        log.debug("Client {} subscribed topics {}", sessionId, topics);
    }

    private void unsubscribe(String sessionId, Set<String> topics) {
        Set<String> subscriptions = sessionTopics.get(sessionId);
        if (subscriptions != null) {
            subscriptions.removeAll(topics);
            if (subscriptions.isEmpty()) {
                subscriptions.addAll(defaultTopics());
            }
            log.debug("Client {} unsubscribed topics {}", sessionId, topics);
        }
    }

    private boolean isSessionSubscribed(String sessionId, Set<String> messageTopics) {
        Set<String> subscriptions = sessionTopics.get(sessionId);
        if (subscriptions == null || subscriptions.isEmpty()) {
            return true;
        }

        for (String topic : messageTopics) {
            if (subscriptions.contains(topic)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> resolveMessageTopics(Object message) {
        if (!(message instanceof MarketMessage)) {
            return Collections.emptySet();
        }

        MarketMessage data = (MarketMessage) message;
        String type = normalizeType(data.getType());
        Set<String> topics = new HashSet<>();

        if (TOPIC_TICKER.equals(type)) {
            topics.add(TOPIC_TICKER);
            return topics;
        }

        if (TOPIC_CANDLE.equals(type)) {
            topics.add(TOPIC_CANDLE);
            CandleSnapshot candle = data.getCandle();
            if (candle != null && StringUtils.hasText(candle.getInterval())) {
                topics.add(TOPIC_CANDLE + "." + normalize(candle.getInterval()));
            }
            return topics;
        }

        if (TOPIC_ORDERBOOK.equals(type)) {
            topics.add(TOPIC_ORDERBOOK);
            OrderBookSnapshot orderBook = data.getOrderBook();
            if (orderBook != null) {
                topics.add(TOPIC_ORDERBOOK + "." + orderBook.getMultiplier());
            }
            return topics;
        }

        if (TOPIC_TRADE.equals(type)) {
            topics.add(TOPIC_TRADE);
            return topics;
        }

        return Collections.emptySet();
    }

    private String normalizeType(String type) {
        String normalized = normalize(type);
        if (normalized == null) {
            return null;
        }

        String compact = normalized.replace("_", "").replace("-", "").replace(" ", "");
        if ("orderbook".equals(compact)) {
            return TOPIC_ORDERBOOK;
        }
        return normalized;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private void removeSession(String sessionId) {
        sessions.remove(sessionId);
        sessionTopics.remove(sessionId);
    }
}
