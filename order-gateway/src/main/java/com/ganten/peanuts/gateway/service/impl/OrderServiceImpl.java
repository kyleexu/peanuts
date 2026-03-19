package com.ganten.peanuts.gateway.service.impl;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.OrderStatus;
import com.ganten.peanuts.common.enums.Source;
import com.ganten.peanuts.gateway.dispatcher.OrderDispatcher;
import com.ganten.peanuts.gateway.model.AcceptedResponse;
import com.ganten.peanuts.gateway.model.OrderSubmitRequest;
import com.ganten.peanuts.gateway.service.OrderService;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderDispatcher orderDispatcher;
    private final TaskExecutor orderDispatchExecutor;

    public OrderServiceImpl(OrderDispatcher orderDispatcher,
            @Qualifier("orderDispatchExecutor") TaskExecutor orderDispatchExecutor) {
        this.orderDispatcher = orderDispatcher;
        this.orderDispatchExecutor = orderDispatchExecutor;
    }

    @Override
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

        return submitOrder(order);
    }

    @Override
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

        orderDispatchExecutor.execute(new Runnable() {
            @Override
            public void run() {
                orderDispatcher.dispatch(order);
            }
        });

        AcceptedResponse response = new AcceptedResponse();
        response.setTrackingId(UUID.randomUUID().toString());
        response.setMessage("Order accepted for async dispatch");
        response.setSource(order.getSource());
        response.setAcceptedAt(System.currentTimeMillis());
        return response;
    }
}
