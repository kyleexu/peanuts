package com.ganten.peanuts.engine.mapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.entity.Trade;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.engine.model.OrderBook;
import com.ganten.peanuts.protocol.model.OrderBookProto;
import com.ganten.peanuts.protocol.model.OrderProto;
import com.ganten.peanuts.protocol.model.TradeProto;
import com.ganten.peanuts.protocol.model.OrderBookProto.OrderSnapshot;

public class ProtocolModelMapper {

    public static Order toDomainOrder(OrderProto command) {
        Order order = new Order();
        order.setOrderId(command.getOrderId());
        order.setUserId(command.getUserId());
        order.setContract(command.getContract());
        order.setSide(command.getSide());
        order.setOrderType(command.getOrderType());
        order.setTimeInForce(command.getTimeInForce());
        order.setPrice(command.getPrice());
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setTotalQuantity(command.getTotalQuantity());
        order.setTimestamp(command.getTimestamp());
        order.setSource(command.getSource());
        order.setAction(command.getAction());
        order.setTargetOrderId(command.getTargetOrderId());
        return order;
    }

    public static TradeProto toTradeEvent(Trade trade) {
        TradeProto event = new TradeProto();
        event.setTradeId(trade.getTradeId());
        event.setBuyOrderId(trade.getBuyOrderId());
        event.setSellOrderId(trade.getSellOrderId());
        event.setBuyUserId(trade.getBuyUserId());
        event.setSellUserId(trade.getSellUserId());
        event.setContract(trade.getContract());
        event.setPrice(trade.getPrice());
        event.setQuantity(trade.getQuantity());
        event.setTimestamp(trade.getTimestamp());
        return event;
    }

    public static OrderBookProto toRawOrderBookSnapshot(Contract contract, OrderBook orderBook) {
        OrderBookProto snapshot = new OrderBookProto();
        snapshot.setContract(contract);
        snapshot.setTimestamp(System.currentTimeMillis());
        snapshot.setBidOrders(toOrderLevels(orderBook.getBuyOrders()));
        snapshot.setAskOrders(toOrderLevels(orderBook.getSellOrders()));
        return snapshot;
    }

    private static List<OrderSnapshot> toOrderLevels(PriorityQueue<Order> orders) {
        List<OrderSnapshot> levels = new ArrayList<OrderSnapshot>(orders.size());
        for (Order order : orders) {
            OrderSnapshot level = new OrderSnapshot();
            level.setOrderId(order.getOrderId());
            level.setUserId(order.getUserId());
            level.setPrice(order.getPrice());
            level.setTotalQuantity(order.getTotalQuantity());
            level.setFilledQuantity(order.getFilledQuantity());
            level.setRemainingQuantity(order.getTotalQuantity().subtract(order.getFilledQuantity()));
            level.setTimestamp(order.getTimestamp());
            levels.add(level);
        }
        return levels;
    }
}
