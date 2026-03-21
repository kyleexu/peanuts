package com.ganten.peanuts.gateway.controller;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ganten.peanuts.common.entity.Order;
import com.ganten.peanuts.gateway.cache.OrderCache;
import com.ganten.peanuts.gateway.model.AcceptedResponse;
import com.ganten.peanuts.gateway.model.OrderSubmitRequest;
import com.ganten.peanuts.gateway.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderCache orderCache;

    public OrderController(OrderService orderService, OrderCache orderCache) {
        this.orderService = orderService;
        this.orderCache = orderCache;
    }

    /**
     * 第 1 步，通过 HTTP 接口接收订单请求
     */
    @PostMapping
    public ResponseEntity<AcceptedResponse> submitOrder(@Valid @RequestBody OrderSubmitRequest request) {
        AcceptedResponse response = orderService.submitOrder(request);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * 查询网关内存中的订单快照（由执行回报更新）；未在本网关下过单的 orderId 返回 404。
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable long orderId) {
        Order order = orderCache.get(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }
}
