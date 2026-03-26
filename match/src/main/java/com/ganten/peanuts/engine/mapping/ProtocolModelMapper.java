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
    private static final int ORDERBOOK_SNAPSHOT_MAX_ORDERS_PER_SIDE = 120;

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
        snapshot.setBidOrders(toOrderLevels(orderBook.getBuyOrders(), ORDERBOOK_SNAPSHOT_MAX_ORDERS_PER_SIDE));
        snapshot.setAskOrders(toOrderLevels(orderBook.getSellOrders(), ORDERBOOK_SNAPSHOT_MAX_ORDERS_PER_SIDE));
        return snapshot;
    }

    private static List<OrderSnapshot> toOrderLevels(PriorityQueue<Order> orders, int maxSize) {
        if (orders == null || orders.isEmpty() || maxSize <= 0) {
            return new ArrayList<OrderSnapshot>(0);
        }
        List<OrderSnapshot> levels = new ArrayList<OrderSnapshot>(Math.min(orders.size(), maxSize));
        int count = 0;
        for (Order order : orders) {
            if (count >= maxSize) {
                break;
            }
            OrderSnapshot level = new OrderSnapshot();
            level.setOrderId(order.getOrderId());
            level.setUserId(order.getUserId());
            level.setPrice(order.getPrice());
            level.setTotalQuantity(order.getTotalQuantity());
            level.setFilledQuantity(order.getFilledQuantity());
            level.setRemainingQuantity(order.getTotalQuantity().subtract(order.getFilledQuantity()));
            level.setTimestamp(order.getTimestamp());
            levels.add(level);
            count++;
        }
        return levels;
    }
}
