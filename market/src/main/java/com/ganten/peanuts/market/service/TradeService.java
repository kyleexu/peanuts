package com.ganten.peanuts.market.service;

import org.springframework.stereotype.Service;

import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.market.model.MarketMessage;
import com.ganten.peanuts.market.model.TradeSnapshot;
import com.ganten.peanuts.market.websocket.WebSocketBroadcaster;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TradeService {

    private final WebSocketBroadcaster webSocketBroadcaster;

    public TradeService(WebSocketBroadcaster webSocketBroadcaster) {
        this.webSocketBroadcaster = webSocketBroadcaster;
    }

    public void onTrade(Trade trade) {
        if (trade == null || trade.getContract() == null || trade.getPrice() == null || trade.getQuantity() == null) {
            return;
        }
        TradeSnapshot snapshot = new TradeSnapshot();
        snapshot.setContract(trade.getContract());
        snapshot.setPrice(trade.getPrice());
        snapshot.setQuantity(trade.getQuantity());
        snapshot.setTimestamp(trade.getTimestamp());
        try {
            webSocketBroadcaster.send(MarketMessage.ofTrade(snapshot));
        } catch (Exception e) {
            log.warn("Failed to broadcast trade update: {}", e.getMessage());
        }
    }
}
