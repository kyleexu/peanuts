package com.ganten.peanuts.market.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.market.model.CandleInterval;
import com.ganten.peanuts.market.model.CandleSnapshot;
import com.ganten.peanuts.market.model.MarketDataMessage;
import com.ganten.peanuts.market.model.TickerSnapshot;
import com.ganten.peanuts.market.websocket.WebSocketBroadcaster;

@Service
public class MarketDataService {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataService.class);
    private static final int MAX_CANDLES_PER_INTERVAL = 2000;

    private final Map<Contract, MarketState> states = new ConcurrentHashMap<Contract, MarketState>();
    private final WebSocketBroadcaster webSocketBroadcaster;

    public MarketDataService(WebSocketBroadcaster webSocketBroadcaster) {
        this.webSocketBroadcaster = webSocketBroadcaster;
    }

    public void onTrade(Trade trade) {
        if (trade == null || trade.getContract() == null || trade.getPrice() == null || trade.getQuantity() == null
                || trade.getQuantity().signum() <= 0 || trade.getPrice().signum() <= 0) {
            return;
        }

        MarketState state = states.computeIfAbsent(trade.getContract(), k -> new MarketState());
        synchronized (state) {
            updateTicker(state.ticker, trade);
            for (CandleInterval interval : CandleInterval.values()) {
                updateCandle(state.candles.get(interval), interval, trade);
            }

            // 推送 Ticker 数据到 WebSocket 客户端
            TickerSnapshot tickerSnapshot = copyTicker(trade.getContract(), state.ticker);
            try {
                webSocketBroadcaster.send(MarketDataMessage.ofTicker(tickerSnapshot));
            } catch (Exception e) {
                logger.warn("Failed to broadcast ticker update: {}", e.getMessage());
            }

            // 推送最新的所有 K线 时间间隔到 WebSocket 客户端
            for (CandleInterval interval : CandleInterval.values()) {
                NavigableMap<Long, CandleMutable> series = state.candles.get(interval);
                if (!series.isEmpty()) {
                    CandleMutable latestCandle = series.lastEntry().getValue();
                    CandleSnapshot candleSnapshot = copyCandle(trade.getContract(), interval, latestCandle);
                    try {
                        webSocketBroadcaster.send(MarketDataMessage.ofCandle(candleSnapshot));
                    } catch (Exception e) {
                        logger.warn("Failed to broadcast candle update: {}", e.getMessage());
                    }
                }
            }
        }
    }

    public TickerSnapshot ticker(Contract contract) {
        MarketState state = states.get(contract);
        if (state == null) {
            return emptyTicker(contract);
        }
        synchronized (state) {
            return copyTicker(contract, state.ticker);
        }
    }

    public List<CandleSnapshot> candles(Contract contract, CandleInterval interval, int limit) {
        int normalizedLimit = limit <= 0 ? 100 : Math.min(limit, 1000);
        MarketState state = states.get(contract);
        if (state == null) {
            return Collections.emptyList();
        }

        synchronized (state) {
            NavigableMap<Long, CandleMutable> series = state.candles.get(interval);
            if (series.isEmpty()) {
                return Collections.emptyList();
            }
            List<CandleSnapshot> snapshots = new ArrayList<CandleSnapshot>();
            for (CandleMutable candle : series.descendingMap().values()) {
                snapshots.add(copyCandle(contract, interval, candle));
                if (snapshots.size() >= normalizedLimit) {
                    break;
                }
            }
            Collections.reverse(snapshots);
            return snapshots;
        }
    }

    private void updateTicker(TickerMutable ticker, Trade trade) {
        BigDecimal turnover = trade.getPrice().multiply(trade.getQuantity());
        ticker.lastPrice = trade.getPrice();
        if (ticker.highPrice == null || trade.getPrice().compareTo(ticker.highPrice) > 0) {
            ticker.highPrice = trade.getPrice();
        }
        if (ticker.lowPrice == null || trade.getPrice().compareTo(ticker.lowPrice) < 0) {
            ticker.lowPrice = trade.getPrice();
        }
        ticker.volume = ticker.volume.add(trade.getQuantity());
        ticker.turnover = ticker.turnover.add(turnover);
        ticker.tradeCount++;
        ticker.lastUpdateTs = trade.getTimestamp();
    }

    private void updateCandle(NavigableMap<Long, CandleMutable> series, CandleInterval interval, Trade trade) {
        long openTime = trade.getTimestamp() - (trade.getTimestamp() % interval.getMillis());
        CandleMutable candle = series.get(Long.valueOf(openTime));
        if (candle == null) {
            candle = new CandleMutable();
            candle.openTime = openTime;
            candle.closeTime = openTime + interval.getMillis() - 1;
            candle.open = trade.getPrice();
            candle.high = trade.getPrice();
            candle.low = trade.getPrice();
            candle.close = trade.getPrice();
            candle.volume = BigDecimal.ZERO;
            candle.turnover = BigDecimal.ZERO;
            candle.tradeCount = 0L;
            series.put(Long.valueOf(openTime), candle);
            trimSeries(series);
        }

        candle.close = trade.getPrice();
        if (trade.getPrice().compareTo(candle.high) > 0) {
            candle.high = trade.getPrice();
        }
        if (trade.getPrice().compareTo(candle.low) < 0) {
            candle.low = trade.getPrice();
        }
        candle.volume = candle.volume.add(trade.getQuantity());
        candle.turnover = candle.turnover.add(trade.getPrice().multiply(trade.getQuantity()));
        candle.tradeCount++;
    }

    private void trimSeries(NavigableMap<Long, CandleMutable> series) {
        while (series.size() > MAX_CANDLES_PER_INTERVAL) {
            series.pollFirstEntry();
        }
    }

    private TickerSnapshot emptyTicker(Contract contract) {
        TickerSnapshot snapshot = new TickerSnapshot();
        snapshot.setContract(contract);
        snapshot.setVolume(BigDecimal.ZERO);
        snapshot.setTurnover(BigDecimal.ZERO);
        snapshot.setTradeCount(0L);
        snapshot.setLastUpdateTs(0L);
        return snapshot;
    }

    private TickerSnapshot copyTicker(Contract contract, TickerMutable ticker) {
        TickerSnapshot snapshot = new TickerSnapshot();
        snapshot.setContract(contract);
        snapshot.setLastPrice(ticker.lastPrice);
        snapshot.setHighPrice(ticker.highPrice);
        snapshot.setLowPrice(ticker.lowPrice);
        snapshot.setVolume(ticker.volume);
        snapshot.setTurnover(ticker.turnover);
        snapshot.setTradeCount(ticker.tradeCount);
        snapshot.setLastUpdateTs(ticker.lastUpdateTs);
        return snapshot;
    }

    private CandleSnapshot copyCandle(Contract contract, CandleInterval interval, CandleMutable candle) {
        CandleSnapshot snapshot = new CandleSnapshot();
        snapshot.setContract(contract);
        snapshot.setInterval(interval.getCode());
        snapshot.setOpenTime(candle.openTime);
        snapshot.setCloseTime(candle.closeTime);
        snapshot.setOpen(candle.open);
        snapshot.setHigh(candle.high);
        snapshot.setLow(candle.low);
        snapshot.setClose(candle.close);
        snapshot.setVolume(candle.volume);
        snapshot.setTurnover(candle.turnover);
        snapshot.setTradeCount(candle.tradeCount);
        return snapshot;
    }

    private static final class MarketState {
        private final TickerMutable ticker = new TickerMutable();
        private final Map<CandleInterval, NavigableMap<Long, CandleMutable>> candles =
                new EnumMap<CandleInterval, NavigableMap<Long, CandleMutable>>(CandleInterval.class);

        private MarketState() {
            for (CandleInterval interval : CandleInterval.values()) {
                candles.put(interval, new TreeMap<Long, CandleMutable>());
            }
        }
    }

    private static final class TickerMutable {
        private BigDecimal lastPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal volume = BigDecimal.ZERO;
        private BigDecimal turnover = BigDecimal.ZERO;
        private long tradeCount;
        private long lastUpdateTs;
    }

    private static final class CandleMutable {
        private long openTime;
        private long closeTime;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private BigDecimal turnover;
        private long tradeCount;
    }
}
