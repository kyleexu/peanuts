package com.ganten.peanuts.gateway.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.OrderAction;
import com.ganten.peanuts.common.enums.OrderStatus;
import com.ganten.peanuts.common.enums.Source;
import com.ganten.peanuts.gateway.account.AccountLockService;
import com.ganten.peanuts.gateway.cache.OrderCache;
import com.ganten.peanuts.gateway.model.AcceptedResponse;
import com.ganten.peanuts.gateway.model.OrderSubmitRequest;
import com.ganten.peanuts.gateway.mapping.OrderProtocolMapper;
import com.ganten.peanuts.protocol.model.LockResponseProto;
import com.ganten.peanuts.gateway.messaging.publisher.OrderPublisher;

@Service
public class OrderService {

    private final OrderPublisher orderPublisher;
    private final AccountLockService accountLockService;
    private final TaskExecutor orderDispatchExecutor;
    private final OrderCache orderCache;

    public OrderService(OrderPublisher orderPublisher, AccountLockService accountLockService,
            @Qualifier("orderDispatchExecutor") TaskExecutor orderDispatchExecutor,
            OrderCache orderCache) {
        this.orderPublisher = orderPublisher;
        this.accountLockService = accountLockService;
        this.orderDispatchExecutor = orderDispatchExecutor;
        this.orderCache = orderCache;
    }

    /**
     * 第 2 步，将订单请求转换为订单实体
     */
    public AcceptedResponse submitOrder(OrderSubmitRequest request) {
        Order order = new Order();
        order.setOrderId(System.nanoTime());
        order.setUserId(request.getUserId());
        order.setContract(request.getContract());
        order.setSide(request.getSide());
        order.setOrderType(request.getOrderType());
        order.setTimeInForce(request.getTimeInForce());
        order.setPrice(request.getPrice());
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setTotalQuantity(request.getTotalQuantity());
        order.setTimestamp(System.currentTimeMillis());
        order.setOrderStatus(OrderStatus.NEW);
        order.setSource(request.getSource() == null ? Source.API : request.getSource());
        order.setAction(request.getAction() == null ? OrderAction.NEW : request.getAction());
        order.setTargetOrderId(request.getTargetOrderId() == null ? 0L : request.getTargetOrderId());

        if ((order.getAction() == OrderAction.CANCEL || order.getAction() == OrderAction.MODIFY)
                && order.getTargetOrderId() <= 0L) {
            throw new IllegalArgumentException("targetOrderId is required for MODIFY/CANCEL action");
        }

        return this.submitOrder(order);
    }


    public AcceptedResponse submitOrder(final Order order) {
        if (order.getOrderId() <= 0L) {
            order.setOrderId(System.nanoTime());
        }
        if (order.getTimestamp() <= 0L) {
            order.setTimestamp(System.currentTimeMillis());
        }
        if (order.getFilledQuantity() == null) {
            order.setFilledQuantity(BigDecimal.ZERO);
        }
        if (order.getOrderStatus() == null) {
            order.setOrderStatus(OrderStatus.NEW);
        }
        if (order.getSource() == null) {
            order.setSource(Source.API);
        }
        if (order.getAction() == null) {
            order.setAction(OrderAction.NEW);
        }

        if (order.getAction() == OrderAction.NEW) {
            LockResponseProto lockResponse = accountLockService.checkAndLock(order);
            if (!lockResponse.isSuccess()) {
                throw new IllegalArgumentException("account lock failed: " + lockResponse.getMessage());
            }
        }

        if (order.getAction() == OrderAction.NEW) {
            orderCache.put(order);
        }

        /**
         * 第 7 步，执行订单，并发布订单
         * 关键: 这里会异步执行订单，并发布订单
         * 这里需要使用 TaskExecutor 异步执行订单，并发布订单
         */
        orderDispatchExecutor.execute(() -> orderPublisher.offer(OrderProtocolMapper.toProto(order)));

        /**
         * 第 8 步，返回订单接受响应
         * 关键: 这里需要返回订单接受响应，并设置订单接受响应的跟踪ID
         * 这里需要使用 AcceptedResponse 返回订单接受响应
         */
        AcceptedResponse response = new AcceptedResponse();
        response.setTrackingId(UUID.randomUUID().toString());
        response.setMessage("Order accepted for async dispatch");
        response.setSource(order.getSource());
        response.setAcceptedAt(System.currentTimeMillis());
        return response;
    }
}
