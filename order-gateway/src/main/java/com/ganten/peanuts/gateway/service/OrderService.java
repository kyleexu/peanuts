package com.ganten.peanuts.gateway.service;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.gateway.model.AcceptedResponse;
import com.ganten.peanuts.gateway.model.OrderSubmitRequest;

public interface OrderService {

    /**
     * 面向 controller
     */
    AcceptedResponse submitOrder(OrderSubmitRequest request);

    /**
     * 内部调用
     */
    AcceptedResponse submitOrder(Order order);
}
