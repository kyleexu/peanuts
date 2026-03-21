package com.ganten.peanuts.market.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.market.model.MarketMessage;
import com.ganten.peanuts.market.model.TickerSnapshot;
import com.ganten.peanuts.market.websocket.WebSocketBroadcaster;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TickerService {

    private final Map<Contract, TickerMutable> tickers = new ConcurrentHashMap<Contract, TickerMutable>();
    private final WebSocketBroadcaster webSocketBroadcaster;

    public TickerService(WebSocketBroadcaster webSocketBroadcaster) {
        this.webSocketBroadcaster = webSocketBroadcaster;
    }

    public void onTrade(Trade trade) {
        if (!isValidTrade(trade)) {
            return;
        }

        TickerMutable ticker = tickers.computeIfAbsent(trade.getContract(), k -> new TickerMutable());
        synchronized (ticker) {
            updateTicker(ticker, trade);
            TickerSnapshot snapshot = copyTicker(trade.getContract(), ticker);
            try {
                webSocketBroadcaster.send(MarketMessage.ofTicker(snapshot));
            } catch (Exception e) {
                log.warn("Failed to broadcast ticker update: {}", e.getMessage());
            }
        }
    }

    public TickerSnapshot ticker(Contract contract) {
        TickerMutable ticker = tickers.get(contract);
        if (ticker == null) {
            return emptyTicker(contract);
        }
        synchronized (ticker) {
            return copyTicker(contract, ticker);
        }
    }

    private boolean isValidTrade(Trade trade) {
        return trade != null && trade.getContract() != null && trade.getPrice() != null && trade.getQuantity() != null
                && trade.getQuantity().signum() > 0 && trade.getPrice().signum() > 0;
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

    private static final class TickerMutable {
        private BigDecimal lastPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal volume = BigDecimal.ZERO;
        private BigDecimal turnover = BigDecimal.ZERO;
        private long tradeCount;
        private long lastUpdateTs;
    }
}
