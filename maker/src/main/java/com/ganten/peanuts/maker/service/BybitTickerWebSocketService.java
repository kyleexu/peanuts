package com.ganten.peanuts.maker.service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.maker.cache.TickerCache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BybitTickerWebSocketService {

    private final boolean enabled;
    private final List<String> websocketUrls;
    private final List<String> symbols;
    private final long reconnectDelayMs;
    private final TickerCache tickerCache;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicInteger endpointCursor = new AtomicInteger(0);

    private volatile WebSocketClient client;

    public BybitTickerWebSocketService(
            TickerCache tickerCache,
            @Value("${maker.bybit.websocket.enabled:true}") boolean enabled,
            @Value("${maker.bybit.websocket.urls:wss://stream.bybit.com/v5/public/spot}") String websocketUrls,
            @Value("${maker.bybit.websocket.symbols:BTCUSDT,ETHUSDT}") String symbols,
            @Value("${maker.bybit.websocket.reconnect-delay-ms:3000}") long reconnectDelayMs) {
        this.tickerCache = tickerCache;
        this.enabled = enabled;
        this.websocketUrls = parseUrls(websocketUrls);
        this.symbols = parseSymbols(symbols);
        this.reconnectDelayMs = Math.max(500L, reconnectDelayMs);
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("Bybit websocket disabled by config.");
            return;
        }
        connect();
    }

    @PreDestroy
    public void stop() {
        shutdown.set(true);
        WebSocketClient ws = this.client;
        if (ws != null) {
            try {
                ws.close();
            } catch (Exception ex) {
                log.debug("Ignore websocket close error: {}", ex.getMessage());
            }
        }
        reconnectExecutor.shutdownNow();
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
                    log.info("Bybit websocket connected: {}", wsUrl);
                    endpointCursor.set(0);
                    sendSubscribe();
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("Bybit websocket closed. url={}, code={}, remote={}, reason={}", wsUrl, code, remote,
                            reason);
                    rotateEndpoint();
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    log.warn("Bybit websocket error. url={}, error={}", wsUrl, ex.getMessage());
                }
            };
            this.client.setConnectionLostTimeout(20);
            this.client.connect();
        } catch (Exception ex) {
            log.warn("Failed to connect Bybit websocket: {}", ex.getMessage());
            rotateEndpoint();
            scheduleReconnect();
        }
    }

    private void sendSubscribe() {
        if (this.client == null || !this.client.isOpen()) {
            return;
        }
        try {
            List<String> args = new ArrayList<String>();
            for (String symbol : symbols) {
                args.add("tickers." + symbol);
            }
            JsonNode payload = objectMapper.createObjectNode()
                    .put("op", "subscribe")
                    .set("args", objectMapper.valueToTree(args));
            this.client.send(objectMapper.writeValueAsString(payload));
            log.info("Bybit websocket subscribed args={}", args);
        } catch (Exception ex) {
            log.warn("Failed to subscribe Bybit ticker stream: {}", ex.getMessage());
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String topic = root.path("topic").asText(null);
            if (topic == null || !topic.startsWith("tickers.")) {
                return;
            }

            String symbol = topic.substring("tickers.".length()).trim().toUpperCase();
            Contract contract = mapSymbol(symbol);
            if (contract == null) {
                return;
            }

            JsonNode data = root.path("data");
            BigDecimal lastPrice = extractLastPrice(data);
            if (lastPrice != null && lastPrice.signum() > 0) {
                log.info("Bybit ticker update. symbol={}, lastPrice={}", symbol, lastPrice);
                tickerCache.putLastPrice(contract, lastPrice);
            }
        } catch (Exception ex) {
            log.debug("Ignore malformed Bybit websocket message: {}", ex.getMessage());
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
        JsonNode lastPriceNode = node.path("lastPrice");
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
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Origin", "https://www.bybit.com");
        headers.put("User-Agent", "peanuts-maker/1.0");
        return headers;
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
            return Collections.singletonList("wss://stream.bybit.com/v5/public/linear");
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
            parsed.add("wss://stream.bybit.com/v5/public/linear");
        }
        return parsed;
    }

    private String pickEndpointUrl() {
        int idx = Math.floorMod(endpointCursor.get(), websocketUrls.size());
        return websocketUrls.get(idx);
    }

    private void rotateEndpoint() {
        endpointCursor.incrementAndGet();
    }

    private void scheduleReconnect() {
        if (shutdown.get()) {
            return;
        }
        reconnectExecutor.schedule(this::connect, reconnectDelayMs, TimeUnit.MILLISECONDS);
    }
}
