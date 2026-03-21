package com.ganten.peanuts.engine.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import com.ganten.peanuts.common.entity.Order;
import lombok.Data;

@Data
public class OrderBook {

    private final PriorityQueue<Order> buyOrders = new PriorityQueue<Order>(11, new Comparator<Order>() {
        @Override
        public int compare(Order left, Order right) {
            int priceCompare = right.getPrice().compareTo(left.getPrice());
            if (priceCompare != 0) {
                return priceCompare;
            }
            return Long.compare(left.getTimestamp(), right.getTimestamp());
        }
    });

    private final PriorityQueue<Order> sellOrders = new PriorityQueue<Order>(11, new Comparator<Order>() {
        @Override
        public int compare(Order left, Order right) {
            int priceCompare = left.getPrice().compareTo(right.getPrice());
            if (priceCompare != 0) {
                return priceCompare;
            }
            return Long.compare(left.getTimestamp(), right.getTimestamp());
        }
    });

    private final Map<Long, Order> ordersById = new HashMap<Long, Order>();
}
