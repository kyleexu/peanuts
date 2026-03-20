package com.ganten.peanuts.gateway.mapping;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.protocol.model.OrderProto;


public class OrderProtocolMapper {

    public static OrderProto toProto(Order order) {
        OrderProto command = new OrderProto();
        command.setOrderId(order.getOrderId());
        command.setUserId(order.getUserId());
        command.setContract(order.getContract());
        command.setSide(order.getSide());
        command.setOrderType(order.getOrderType());
        command.setTimeInForce(order.getTimeInForce());
        command.setPrice(order.getPrice());
        command.setTotalQuantity(order.getTotalQuantity());
        command.setTimestamp(order.getTimestamp());
        command.setSource(order.getSource());
        command.setAction(order.getAction());
        command.setTargetOrderId(order.getTargetOrderId());
        return command;
    }
}
