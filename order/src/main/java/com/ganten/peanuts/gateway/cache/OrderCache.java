package com.ganten.peanuts.gateway.cache;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.common.enums.ExecType;
import com.ganten.peanuts.common.enums.OrderStatus;
import com.ganten.peanuts.protocol.model.ExecutionReportProto;

import lombok.extern.slf4j.Slf4j;

/**
 * 网关侧订单内存视图：下单时写入，消费执行回报后更新状态与成交量。
 */
@Slf4j
@Component
public class OrderCache {

    private final ConcurrentHashMap<Long, Order> byOrderId = new ConcurrentHashMap<>();

    public void put(Order order) {
        byOrderId.put(order.getOrderId(), order);
    }

    public Order get(long orderId) {
        return byOrderId.get(orderId);
    }

    /**
     * 根据撮合侧执行回报更新缓存中的订单（仅更新已缓存的 orderId）。
     */
    public void applyExecutionReport(ExecutionReportProto report) {
        if (report == null) {
            return;
        }
        long orderId = report.getOrderId();
        Order order = byOrderId.get(orderId);
        if (order == null) {
            log.trace("ExecutionReport ignored: orderId={} not in gateway cache", orderId);
            return;
        }
        synchronized (order) {
            ExecType execType = report.getExecType();
            if (execType == ExecType.TRADE) {
                BigDecimal delta = report.getMatchedQuantity();
                if (delta != null) {
                    BigDecimal cur = order.getFilledQuantity() != null ? order.getFilledQuantity() : BigDecimal.ZERO;
                    order.setFilledQuantity(cur.add(delta));
                }
                if (report.getOrderStatus() != null) {
                    order.setOrderStatus(report.getOrderStatus());
                }
            } else if (execType == ExecType.CANCELED) {
                order.setOrderStatus(OrderStatus.CANCELED);
            } else if (execType == ExecType.REJECTED) {
                order.setOrderStatus(OrderStatus.REJECTED);
            }
            if (report.getTimestamp() > 0L) {
                order.setTimestamp(report.getTimestamp());
            }
        }
    }
}
