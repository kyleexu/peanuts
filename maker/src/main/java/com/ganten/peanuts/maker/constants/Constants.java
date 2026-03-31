package com.ganten.peanuts.maker.constants;

import java.math.BigDecimal;

public final class Constants {

    private Constants() {}

    // ── MakerLadderScheduler ──────────────────────────────────────────────────
    public static final long MAKER_FIXED_DELAY_MS = 1000L;
    public static final BigDecimal MAKER_MIN_AVAILABLE_QUOTE = new BigDecimal("1000");
    public static final BigDecimal MAKER_MIN_AVAILABLE_BASE = new BigDecimal("0.5");
    public static final int LADDER_LEVELS = 20;
    public static final BigDecimal LADDER_STEP_BPS = new BigDecimal("1");
    public static final BigDecimal MIN_LADDER_NOTIONAL_USDT = new BigDecimal("120");
    public static final BigDecimal MAX_LADDER_NOTIONAL_USDT = new BigDecimal("500");
    public static final int MAX_ACTIVE_ORDERS_PER_CONTRACT = 40;
    public static final int MIN_ORDERS_PER_SIDE = 20;
    public static final BigDecimal REBALANCE_DEVIATION_BPS = new BigDecimal("15");
    public static final long REBALANCE_INTERVAL_MS = 15000L;
    public static final BigDecimal INVENTORY_TARGET_BASE_QTY = BigDecimal.ZERO;
    public static final BigDecimal INVENTORY_SOFT_LIMIT_BASE_QTY = new BigDecimal("30");
    public static final BigDecimal INVENTORY_BIAS_CAP = new BigDecimal("0.5");

    // ── TakerFlowScheduler ────────────────────────────────────────────────────
    public static final long TAKER_FIXED_DELAY_MS = 200L;
    public static final BigDecimal TAKER_MIN_AVAILABLE_QUOTE = new BigDecimal("1000");
    public static final BigDecimal TAKER_MIN_AVAILABLE_BASE = new BigDecimal("0.5");
    public static final BigDecimal MIN_TAKER_NOTIONAL_USDT = new BigDecimal("30");
    public static final BigDecimal MAX_TAKER_NOTIONAL_USDT = new BigDecimal("180");
    public static final BigDecimal TAKER_SWEEP_BPS = new BigDecimal("1.5");
    public static final long TAKER_RESULT_TIMEOUT_MS = 1500L;

    // ── BalanceCache ──────────────────────────────────────────────────────────
    // 同步用户余额的时间间隔，单位毫秒
    public static final long BALANCE_CACHE_SYNC_DELAY_MS = 1000L;

    // ── Ticker WebSocket ──────────────────────────────────────────────────────
    public static final boolean TICKER_WEBSOCKET_ENABLED = true;
    public static final String TICKER_WEBSOCKET_URL = "wss://stream-pro.hashkey.com/quote/ws/v2";
    public static final String TICKER_WEBSOCKET_SYMBOLS = "BTCUSDT,ETHUSDT";
    public static final long TICKER_RECONNECT_DELAY_MS = 3000L;
    public static final long TICKER_HEARTBEAT_INTERVAL_MS = 10000L;

    // ── API URLs ──────────────────────────────────────────────────────────────
    public static final String ORDER_URL = "http://localhost:8080";
    public static final String ACCOUNT_URL = "http://localhost:8081";
    public static final String MARKET_URL = "http://localhost:8082";
    public static final long BALANCE_CLIENT_WARN_INTERVAL_MS = 30000L;
}
