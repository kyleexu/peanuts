package com.ganten.peanuts.market.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.enums.CandleInterval;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.market.model.CandleSnapshot;
import com.ganten.peanuts.market.model.MarketMessage;
import com.ganten.peanuts.market.websocket.WebSocketBroadcaster;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CandleService {

    private static final int MAX_CANDLES_PER_INTERVAL = 2000;

    private final Map<Contract, Map<CandleInterval, NavigableMap<Long, CandleMutable>>> candlesByContract =
            new ConcurrentHashMap<Contract, Map<CandleInterval, NavigableMap<Long, CandleMutable>>>();
    private final WebSocketBroadcaster webSocketBroadcaster;

    public CandleService(WebSocketBroadcaster webSocketBroadcaster) {
        this.webSocketBroadcaster = webSocketBroadcaster;
    }

    public void onTrade(Trade trade) {
        if (!isValidTrade(trade)) {
            return;
        }
        Map<CandleInterval, NavigableMap<Long, CandleMutable>> candles =
                candlesByContract.computeIfAbsent(trade.getContract(), k -> initSeriesByInterval());
        synchronized (candles) {
            for (CandleInterval interval : CandleInterval.values()) {
                NavigableMap<Long, CandleMutable> series = candles.get(interval);
                updateCandle(series, interval, trade);
                if (!series.isEmpty()) {
                    CandleSnapshot snapshot = copyCandle(trade.getContract(), interval, series.lastEntry().getValue());
                    try {
                        webSocketBroadcaster.send(MarketMessage.ofCandle(snapshot));
                    } catch (Exception e) {
                        log.warn("Failed to broadcast candle update: {}", e.getMessage());
                    }
                }
            }
        }
    }

    public List<CandleSnapshot> candles(Contract contract, CandleInterval interval, int limit) {
        int normalizedLimit = limit <= 0 ? 100 : Math.min(limit, 1000);
        Map<CandleInterval, NavigableMap<Long, CandleMutable>> candles = candlesByContract.get(contract);
        if (candles == null) {
            return Collections.emptyList();
        }

        synchronized (candles) {
            NavigableMap<Long, CandleMutable> series = candles.get(interval);
            if (series == null || series.isEmpty()) {
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

    private boolean isValidTrade(Trade trade) {
        return trade != null && trade.getContract() != null && trade.getPrice() != null && trade.getQuantity() != null
                && trade.getQuantity().signum() > 0 && trade.getPrice().signum() > 0;
    }

    private Map<CandleInterval, NavigableMap<Long, CandleMutable>> initSeriesByInterval() {
        Map<CandleInterval, NavigableMap<Long, CandleMutable>> seriesByInterval =
                new EnumMap<CandleInterval, NavigableMap<Long, CandleMutable>>(CandleInterval.class);
        for (CandleInterval interval : CandleInterval.values()) {
            seriesByInterval.put(interval, new TreeMap<Long, CandleMutable>());
        }
        return seriesByInterval;
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

