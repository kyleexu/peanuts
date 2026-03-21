package com.ganten.peanuts.gateway.controller;

import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ganten.peanuts.gateway.model.AcceptedResponse;
import com.ganten.peanuts.gateway.model.OrderSubmitRequest;
import com.ganten.peanuts.gateway.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 第 1 步，通过 HTTP 接口接收订单请求
     */
    @PostMapping
    public ResponseEntity<AcceptedResponse> submitOrder(@Valid @RequestBody OrderSubmitRequest request) {
        AcceptedResponse response = orderService.submitOrder(request);
        return ResponseEntity.accepted().body(response);
    }
}
