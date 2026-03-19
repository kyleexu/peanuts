package com.ganten.peanuts.gateway.dispatcher;

import com.ganten.peanuts.common.entity.Order;

public interface OrderDispatcher {

    void dispatch(Order order);
}
