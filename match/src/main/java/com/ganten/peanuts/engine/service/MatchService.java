package com.ganten.peanuts.engine.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.enums.OrderAction;
import com.ganten.peanuts.common.enums.OrderStatus;
import com.ganten.peanuts.common.enums.Side;
import com.ganten.peanuts.engine.messaging.publisher.ExecutionReportPublisher;
import com.ganten.peanuts.engine.messaging.publisher.TradePublisher;
import com.ganten.peanuts.engine.model.OrderBook;
import com.ganten.peanuts.engine.utils.ExecutionReportBuilder;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;
import com.ganten.peanuts.protocol.model.TradeProto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MatchService {

    private final Map<Contract, OrderBook> books = new HashMap<Contract, OrderBook>();
    private final ExecutionReportPublisher executionReportPublisher;
    private final TradePublisher tradePublisher;
    private final AtomicLong tradeIdGenerator = new AtomicLong(System.currentTimeMillis() * 1_000L);

    public MatchService(ExecutionReportPublisher executionReportPublisher, TradePublisher tradePublisher) {
        this.executionReportPublisher = executionReportPublisher;
        this.tradePublisher = tradePublisher;
    }

    /**
     * 获取指定合约的订单簿（已同步）
     *
     * @param contract 合约
     * @return 订单簿（如果不存在则返回空订单簿）
     */
    public synchronized OrderBook getOrderBook(Contract contract) {
        return orderBook(contract);
    }

    public synchronized void match(Order order) {
        // 修复订单状态、动作、目标订单ID
        this.normalize(order);

        // 如果是取消订单，那就直接取消
        if (order.getAction() == OrderAction.CANCEL) {
            this.cancel(order);
            return;
        }
        // 如果是修改订单，需要先取消目标订单，然后创建新订单，然后进行撮合
        if (order.getAction() == OrderAction.MODIFY) {
            long targetOrderId = resolveTargetOrderId(order);
            Order canceledOrder = cancelExisting(order.getContract(), targetOrderId);
            if (canceledOrder == null) {
                return;
            }
            order.setOrderId(targetOrderId);
            order.setFilledQuantity(BigDecimal.ZERO);
            order.setOrderStatus(OrderStatus.NEW);
            order.setAction(OrderAction.NEW);
        }

        // 如果不是取消或修改订单，则是新到达的订单，需要进行撮合
        this.newOrder(order);
    }

    private void newOrder(Order incomingOrder) {
        log.info("新订单到达, 订单: {}", incomingOrder);
        OrderBook book = this.orderBook(incomingOrder.getContract());

        // 获取相反方向的订单队列
        PriorityQueue<Order> oppositeQueue = incomingOrder.getSide() == Side.BUY ? book.getSellOrders()
                : book.getBuyOrders();

        // 撮合
        while (!oppositeQueue.isEmpty() && remaining(incomingOrder).compareTo(BigDecimal.ZERO) > 0) {
            // 获取相反方向的订单队列中的第一个订单
            Order restingOrder = oppositeQueue.peek();
            // 如果新订单和待成交订单的价格没有交叉，则跳出撮合
            if (!isCrossed(incomingOrder, restingOrder)) {
                break;
            }

            // 计算成交数量和成交价格: 成交数量为新订单和待成交订单的剩余数量中的较小值
            BigDecimal matchedQuantity = remaining(incomingOrder).min(remaining(restingOrder));
            // 成交价格为待成交订单的价格
            BigDecimal matchedPrice = restingOrder.getPrice();
            log.info("订单成交, 新订单: {}, 待成交订单: {}, 成交数量: {}, 成交价格: {}", incomingOrder.getOrderId(),
                    restingOrder.getOrderId(), matchedQuantity, matchedPrice);
            // 填充新订单和待成交订单
            this.updateFilledQuantity(incomingOrder, matchedQuantity);
            this.updateFilledQuantity(restingOrder, matchedQuantity);

            // 发布交易和交易执行报告
            this.publishTradeAndReport(incomingOrder, restingOrder, matchedPrice, matchedQuantity);

            // 如果新来的订单将对向的第一个订单完全成交，则从订单簿中移除
            if (remaining(restingOrder).compareTo(BigDecimal.ZERO) == 0) {
                oppositeQueue.poll();
                book.getOrdersById().remove(restingOrder.getOrderId());
            }
        }

        if (remaining(incomingOrder).compareTo(BigDecimal.ZERO) > 0) {
            if (incomingOrder.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
                // 如果新来的订单有已成交数量，则将订单状态设置为部分成交
                incomingOrder.setOrderStatus(OrderStatus.PARTIALLY_FILLED);
            } else {
                // 如果新来的订单没有已成交数量，则将订单状态设置为新订单
                incomingOrder.setOrderStatus(OrderStatus.NEW);
            }
            // 如果新来的订单还有剩余数量，则将新来的订单添加到订单簿中
            addToBook(book, incomingOrder);
        } else {
            // 如果新来的订单没有剩余数量，则将订单状态设置为已成交
            incomingOrder.setOrderStatus(OrderStatus.FILLED);
        }
    }

    /**
     * 第 10 步，提交到撮合引擎，撮合完成之后，发布交易和交易执行报告
     * 关键: 撮合完成之后，发布交易和交易执行报告
     */
    private void publishTradeAndReport(Order incomingOrder, Order restingOrder, BigDecimal matchedPrice,
            BigDecimal matchedQuantity) {

        TradeProto trade = new TradeProto();
        long tradeId = tradeIdGenerator.incrementAndGet();
        trade.setTradeId(tradeId);
        if (incomingOrder.getSide() == Side.BUY) {
            trade.setBuyOrderId(incomingOrder.getOrderId());
            trade.setSellOrderId(restingOrder.getOrderId());
            trade.setBuyUserId(incomingOrder.getUserId());
            trade.setSellUserId(restingOrder.getUserId());
        } else {
            trade.setBuyOrderId(restingOrder.getOrderId());
            trade.setSellOrderId(incomingOrder.getOrderId());
            trade.setBuyUserId(restingOrder.getUserId());
            trade.setSellUserId(incomingOrder.getUserId());
        }
        trade.setContract(incomingOrder.getContract());
        trade.setPrice(matchedPrice);
        trade.setQuantity(matchedQuantity);
        trade.setTimestamp(System.currentTimeMillis());

        /**
         * 发布交易
         */
        tradePublisher.offer(trade);

        /**
         * 发布交易执行报告，对于成交的订单，需要发布两条交易执行报告
         * 一条是买入订单的执行报告
         * 一条是卖出订单的执行报告
         * 两条交易执行报告的 tradeId 相同
         */
        ExecutionReportProto buyReport = ExecutionReportBuilder.buildTradeReport(
                incomingOrder,
                trade.getBuyOrderId(),
                trade.getSellOrderId(),
                matchedPrice,
                matchedQuantity,
                tradeId);
        executionReportPublisher.offer(buyReport);
        ExecutionReportProto sellReport = ExecutionReportBuilder.buildTradeReport(
                restingOrder,
                trade.getBuyOrderId(),
                trade.getSellOrderId(),
                matchedPrice,
                matchedQuantity,
                tradeId);
        executionReportPublisher.offer(sellReport);
    }

    private void cancel(Order cancelOrder) {
        long targetOrderId = resolveTargetOrderId(cancelOrder);
        Order existingOrder = cancelExisting(cancelOrder.getContract(), targetOrderId);
        if (existingOrder == null) {
            return;
        }
        existingOrder.setOrderStatus(OrderStatus.CANCELED);
        ExecutionReportProto cancelReport = ExecutionReportBuilder.buildCancelReport(existingOrder);
        executionReportPublisher.offer(cancelReport);
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
        // 计算剩余数量
        return totalQuantity.subtract(filledQuantity);
    }

    /**
     * 更新订单的已成交数量，
     * 如果已成交数量达到订单总数量，则将订单状态设置为已成交
     * 否则将订单状态设置为部分成交
     *
     * @param order    订单
     * @param quantity 成交数量
     */
    private void updateFilledQuantity(Order order, BigDecimal quantity) {
        BigDecimal filledQuantity = order.getFilledQuantity() == null ? BigDecimal.ZERO : order.getFilledQuantity();
        BigDecimal newFilledQuantity = filledQuantity.add(quantity);

        order.setFilledQuantity(newFilledQuantity);
        if (remaining(order).compareTo(BigDecimal.ZERO) == 0) {
            order.setOrderStatus(OrderStatus.FILLED);
        } else {
            order.setOrderStatus(OrderStatus.PARTIALLY_FILLED);
        }
    }
}
