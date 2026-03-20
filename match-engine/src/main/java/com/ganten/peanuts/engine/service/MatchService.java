package com.ganten.peanuts.engine.service;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.*;
import com.ganten.peanuts.engine.model.OrderBook;
import com.ganten.peanuts.engine.utils.ExecutionReportBuilder;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;

@Service
public class MatchService {

    private final Map<Contract, OrderBook> books = new HashMap<Contract, OrderBook>();

    /**
     * 获取指定合约的订单簿（已同步）
     *
     * @param contract 合约
     * @return 订单簿（如果不存在则返回空订单簿）
     */
    public synchronized OrderBook getOrderBook(Contract contract) {
        return orderBook(contract);
    }

    public synchronized List<ExecutionReportProto> match(Order order) {
        normalize(order);

        if (order.getAction() == OrderAction.CANCEL) {
            return cancel(order);
        }
        if (order.getAction() == OrderAction.MODIFY) {
            long targetOrderId = resolveTargetOrderId(order);
            Order canceledOrder = cancelExisting(order.getContract(), targetOrderId);
            if (canceledOrder == null) {
                return Collections.emptyList();
            }
            order.setOrderId(targetOrderId);
            order.setFilledQuantity(BigDecimal.ZERO);
            order.setOrderStatus(OrderStatus.NEW);
            order.setAction(OrderAction.NEW);
        }

        return newOrder(order);
    }

    private List<ExecutionReportProto> newOrder(Order incomingOrder) {
        List<ExecutionReportProto> reports = new ArrayList<ExecutionReportProto>();
        OrderBook book = this.orderBook(incomingOrder.getContract());
        PriorityQueue<Order> oppositeQueue =
                incomingOrder.getSide() == Side.BUY ? book.getSellOrders() : book.getBuyOrders();

        while (!oppositeQueue.isEmpty() && remaining(incomingOrder).compareTo(BigDecimal.ZERO) > 0) {
            Order restingOrder = oppositeQueue.peek();
            if (!isCrossed(incomingOrder, restingOrder)) {
                break;
            }

            BigDecimal matchedQuantity = remaining(incomingOrder).min(remaining(restingOrder));
            BigDecimal matchedPrice = restingOrder.getPrice();

            fill(incomingOrder, matchedQuantity);
            fill(restingOrder, matchedQuantity);

            reports.add(ExecutionReportBuilder.buildTradeReport(incomingOrder, restingOrder.getOrderId(), matchedPrice,
                    matchedQuantity));
            reports.add(ExecutionReportBuilder.buildTradeReport(restingOrder, incomingOrder.getOrderId(), matchedPrice,
                    matchedQuantity));

            if (remaining(restingOrder).compareTo(BigDecimal.ZERO) == 0) {
                oppositeQueue.poll();
                book.getOrdersById().remove(restingOrder.getOrderId());
            }
        }

        if (remaining(incomingOrder).compareTo(BigDecimal.ZERO) > 0) {
            if (incomingOrder.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
                incomingOrder.setOrderStatus(OrderStatus.PARTIALLY_FILLED);
            } else {
                incomingOrder.setOrderStatus(OrderStatus.NEW);
            }
            addToBook(book, incomingOrder);
        } else {
            incomingOrder.setOrderStatus(OrderStatus.FILLED);
        }

        return reports;
    }

    private List<ExecutionReportProto> cancel(Order cancelOrder) {
        List<ExecutionReportProto> reports = new ArrayList<ExecutionReportProto>();
        long targetOrderId = resolveTargetOrderId(cancelOrder);
        Order existingOrder = cancelExisting(cancelOrder.getContract(), targetOrderId);
        if (existingOrder != null) {
            existingOrder.setOrderStatus(OrderStatus.CANCELED);
            reports.add(ExecutionReportBuilder.buildReport(existingOrder, ExecType.CANCELED, existingOrder.getPrice(),
                    BigDecimal.ZERO));
        }
        return reports;
    }

    private Order cancelExisting(Contract contract, long targetOrderId) {
        OrderBook book = this.orderBook(contract);
        Order existingOrder = book.getOrdersById().remove(targetOrderId);
        if (existingOrder == null) {
            return null;
        }

        if (existingOrder.getSide() == Side.BUY) {
            book.getBuyOrders().remove(existingOrder);
        } else {
            book.getSellOrders().remove(existingOrder);
        }
        return existingOrder;
    }

    private long resolveTargetOrderId(Order order) {
        if (order.getTargetOrderId() > 0L) {
            return order.getTargetOrderId();
        }
        return order.getOrderId();
    }

    private void addToBook(OrderBook book, Order order) {
        book.getOrdersById().put(order.getOrderId(), order);
        if (order.getSide() == Side.BUY) {
            book.getBuyOrders().offer(order);
        } else {
            book.getSellOrders().offer(order);
        }
    }

    private OrderBook orderBook(Contract contract) {
        OrderBook orderBook = books.get(contract);
        if (orderBook == null) {
            orderBook = new OrderBook();
            books.put(contract, orderBook);
        }
        return orderBook;
    }

    private void normalize(Order order) {
        if (order.getFilledQuantity() == null) {
            order.setFilledQuantity(BigDecimal.ZERO);
        }
        if (order.getAction() == null) {
            order.setAction(OrderAction.NEW);
        }
        if (order.getOrderStatus() == null) {
            order.setOrderStatus(OrderStatus.NEW);
        }
    }

    private boolean isCrossed(Order incomingOrder, Order restingOrder) {
        if (incomingOrder.getPrice() == null || restingOrder.getPrice() == null) {
            return false;
        }
        if (incomingOrder.getSide() == Side.BUY) {
            return incomingOrder.getPrice().compareTo(restingOrder.getPrice()) >= 0;
        }
        return incomingOrder.getPrice().compareTo(restingOrder.getPrice()) <= 0;
    }

    private BigDecimal remaining(Order order) {
        BigDecimal filledQuantity = order.getFilledQuantity() == null ? BigDecimal.ZERO : order.getFilledQuantity();
        BigDecimal totalQuantity = order.getTotalQuantity() == null ? BigDecimal.ZERO : order.getTotalQuantity();
        return totalQuantity.subtract(filledQuantity);
    }

    private void fill(Order order, BigDecimal quantity) {
        order.setFilledQuantity(order.getFilledQuantity().add(quantity));
        if (remaining(order).compareTo(BigDecimal.ZERO) == 0) {
            order.setOrderStatus(OrderStatus.FILLED);
        } else {
            order.setOrderStatus(OrderStatus.PARTIALLY_FILLED);
        }
    }
}
