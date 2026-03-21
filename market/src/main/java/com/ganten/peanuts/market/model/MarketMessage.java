package com.ganten.peanuts.market.model;

import java.io.Serializable;

/**
 * WebSocket 市场数据推送消息
 */
public class MarketMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 消息类型: ticker / candle / orderBook
     */
    private String type;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * Ticker 数据快照
     */
    private TickerSnapshot ticker;

    /**
     * K线数据快照
     */
    private CandleSnapshot candle;

    /**
     * 订单簿聚合快照
     */
    private OrderBookSnapshot orderBook;

    public MarketMessage() {}

    public MarketMessage(String type, long timestamp) {
        this.type = type;
        this.timestamp = timestamp;
    }

    public static MarketMessage ofTicker(TickerSnapshot ticker) {
        MarketMessage msg = new MarketMessage("ticker", System.currentTimeMillis());
        msg.ticker = ticker;
        return msg;
    }

    public static MarketMessage ofCandle(CandleSnapshot candle) {
        MarketMessage msg = new MarketMessage("candle", System.currentTimeMillis());
        msg.candle = candle;
        return msg;
    }

    public static MarketMessage ofOrderBook(OrderBookSnapshot orderBook) {
        MarketMessage msg = new MarketMessage("orderBook", System.currentTimeMillis());
        msg.orderBook = orderBook;
        return msg;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public TickerSnapshot getTicker() {
        return ticker;
    }

    public void setTicker(TickerSnapshot ticker) {
        this.ticker = ticker;
    }

    public CandleSnapshot getCandle() {
        return candle;
    }

    public void setCandle(CandleSnapshot candle) {
        this.candle = candle;
    }

    public OrderBookSnapshot getOrderBook() {
        return orderBook;
    }

    public void setOrderBook(OrderBookSnapshot orderBook) {
        this.orderBook = orderBook;
    }
}
