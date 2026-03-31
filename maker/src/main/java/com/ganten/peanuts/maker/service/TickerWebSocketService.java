package com.ganten.peanuts.maker.service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.util.JsonUtils;
import com.ganten.peanuts.maker.cache.TickerCache;
import com.ganten.peanuts.maker.constants.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TickerWebSocketService {

    private final TickerCache tickerCache;

    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicInteger endpointCursor = new AtomicInteger(0);
    private final AtomicLong lastPongAt = new AtomicLong(0L);

    private volatile WebSocketClient client;
    private volatile ScheduledFuture<?> heartbeatFuture;

    public TickerWebSocketService(TickerCache tickerCache) {
        this.tickerCache = tickerCache;
    }

    @PostConstruct
    public void start() {
        if (!Constants.TICKER_WEBSOCKET_ENABLED) {
            log.info("Ticker websocket disabled by config.");
            return;
        }
        connect();
    }

    @PreDestroy
    public void stop() {
        shutdown.set(true);
        stopHeartbeat();
        WebSocketClient ws = this.client;
        if (ws != null) {
            try {
                ws.close();
            } catch (Exception ex) {
                log.debug("Ignore websocket close error: {}", ex.getMessage());
            }
        }
        reconnectExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    private synchronized void connect() {
        if (shutdown.get()) {
            return;
        }
        if (this.client != null) {
            ReadyState state = this.client.getReadyState();
            if (state == ReadyState.OPEN || state == ReadyState.NOT_YET_CONNECTED) {
                return;
            }
        }

        try {
            final String wsUrl = pickEndpointUrl();
            this.client = new WebSocketClient(new URI(wsUrl), new Draft_6455(), defaultHeaders(), 10000) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("HashKey ticker websocket connected: {}", wsUrl);
                    endpointCursor.set(0);
                    lastPongAt.set(System.currentTimeMillis());
                    sendSubscribe();
                    startHeartbeat();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    stopHeartbeat();
                    log.warn("HashKey ticker websocket closed. url={}, code={}, remote={}, reason={}", wsUrl, code,
                            remote, reason);
                    rotateEndpoint();
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    stopHeartbeat();
                    log.warn("HashKey ticker websocket error. url={}, error={}", wsUrl, ex.getMessage());
                }
            };
            this.client.setConnectionLostTimeout(20);
            this.client.connect();
        } catch (Exception ex) {
            log.warn("Failed to connect HashKey ticker websocket: {}", ex.getMessage());
            rotateEndpoint();
            scheduleReconnect();
        }
    }

    private void sendSubscribe() {
        if (this.client == null || !this.client.isOpen()) {
            return;
        }
        try {
            for (String symbol : parseSymbols(Constants.TICKER_WEBSOCKET_SYMBOLS)) {
                JsonNode payload = JsonUtils.createObjectNode().put("topic", "realtimes").put("event", "sub")
                        .set("params", JsonUtils.createObjectNode().put("symbol", symbol));
                this.client.send(JsonUtils.toJson(payload));
            }
            log.info("HashKey ticker websocket subscribed symbols={}", Constants.TICKER_WEBSOCKET_SYMBOLS);
        } catch (Exception ex) {
            log.warn("Failed to subscribe HashKey ticker stream: {}", ex.getMessage());
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = JsonUtils.readTree(message);
            JsonNode pongNode = root.path("pong");
            if (!pongNode.isMissingNode() && !pongNode.isNull()) {
                lastPongAt.set(pongNode.asLong(System.currentTimeMillis()));
                log.debug("HashKey ticker websocket pong={}", pongNode.asLong());
                return;
            }

            if (!"realtimes".equals(root.path("topic").asText(null))) {
                return;
            }

            JsonNode data = root.path("data");
            if (data == null || data.isMissingNode() || data.isNull()) {
                return;
            }

            String symbol = data.path("s").asText(null);
            Contract contract = mapSymbol(symbol);
            if (contract == null) {
                return;
            }

            BigDecimal lastPrice = extractLastPrice(data);
            if (lastPrice != null && lastPrice.signum() > 0) {
                log.info("HashKey ticker update. symbol={}, lastPrice={}", symbol, lastPrice);
                tickerCache.putLastPrice(contract, lastPrice);
            }
        } catch (Exception ex) {
            log.debug("Ignore malformed HashKey websocket message: {}", ex.getMessage());
        }
    }

    private BigDecimal extractLastPrice(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return null;
        }

        if (data.isArray() && data.size() > 0) {
            JsonNode first = data.get(0);
            return parseLastPriceNode(first);
        }
        if (data.isObject()) {
            return parseLastPriceNode(data);
        }
        return null;
    }

    private BigDecimal parseLastPriceNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode lastPriceNode = node.path("c");
        if (lastPriceNode.isMissingNode() || lastPriceNode.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(lastPriceNode.asText("0"));
        } catch (Exception ex) {
            return null;
        }
    }

    private Contract mapSymbol(String symbol) {
        if ("BTCUSDT".equals(symbol)) {
            return Contract.BTC_USDT;
        }
        if ("ETHUSDT".equals(symbol)) {
            return Contract.ETH_USDT;
        }
        return null;
    }

    private Map<String, String> defaultHeaders() {
        return Collections.singletonMap("User-Agent", "peanuts-maker/1.0");
    }

    private List<String> parseSymbols(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = raw.split(",");
        List<String> parsed = new ArrayList<String>(parts.length);
        for (String part : parts) {
            String value = part == null ? null : part.trim().toUpperCase();
            if (value != null && !value.isEmpty()) {
                parsed.add(value);
            }
        }
        return parsed;
    }

    private List<String> parseUrls(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.singletonList("wss://stream-pro.hashkey.com/quote/ws/v2");
        }
        String[] parts = raw.split(",");
        List<String> parsed = new ArrayList<String>(parts.length);
        for (String part : parts) {
            String value = part == null ? null : part.trim();
            if (value != null && !value.isEmpty()) {
                parsed.add(value);
            }
        }
        if (parsed.isEmpty()) {
            parsed.add("wss://stream-pro.hashkey.com/quote/ws/v2");
        }
        return parsed;
    }

    private String pickEndpointUrl() {
        List<String> urls = parseUrls(Constants.TICKER_WEBSOCKET_URL);
        int idx = Math.floorMod(endpointCursor.get(), urls.size());
        return urls.get(idx);
    }

    private void rotateEndpoint() {
        endpointCursor.incrementAndGet();
    }

    private void scheduleReconnect() {
        if (shutdown.get()) {
            return;
        }
        reconnectExecutor.schedule(this::connect, Constants.TICKER_RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void startHeartbeat() {
        stopHeartbeat();
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(this::sendPing, Constants.TICKER_HEARTBEAT_INTERVAL_MS,
                Constants.TICKER_HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
    }

    private void sendPing() {
        WebSocketClient ws = this.client;
        if (shutdown.get() || ws == null || !ws.isOpen()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            JsonNode payload = JsonUtils.createObjectNode().put("ping", now);
            ws.send(JsonUtils.toJson(payload));
            log.debug("HashKey ticker websocket ping={}", now);
        } catch (Exception ex) {
            log.warn("Failed to send HashKey ticker heartbeat: {}", ex.getMessage());
        }
    }
}
