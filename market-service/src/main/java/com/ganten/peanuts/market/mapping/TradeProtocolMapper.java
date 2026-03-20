package com.ganten.peanuts.market.mapping;

import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.protocol.model.TradeProto;


public class TradeProtocolMapper {

    public static Trade toDomain(TradeProto event) {
        Trade trade = new Trade();
        trade.setTradeId(event.getTradeId());
        trade.setBuyOrderId(event.getBuyOrderId());
        trade.setSellOrderId(event.getSellOrderId());
        trade.setBuyUserId(event.getBuyUserId());
        trade.setSellUserId(event.getSellUserId());
        trade.setContract(event.getContract());
        trade.setPrice(event.getPrice());
        trade.setQuantity(event.getQuantity());
        trade.setTimestamp(event.getTimestamp());
        return trade;
    }
}
