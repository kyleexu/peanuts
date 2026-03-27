package com.ganten.peanuts.maker.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ganten.peanuts.maker.model.OrderSubmitRequest;

@Component
public class OrderClient {

    private final String orderApiBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public OrderClient(@Value("${maker.random-order.order-api-base-url:http://localhost:8080}") String orderApiBaseUrl) {
        this.orderApiBaseUrl = orderApiBaseUrl;
    }

    public void submitOrder(OrderSubmitRequest order) {
        String url = this.orderApiBaseUrl.trim() + "/api/orders";
        HttpEntity<OrderSubmitRequest> requestEntity = new HttpEntity<OrderSubmitRequest>(order);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode().value() != 202) {
            throw new IllegalStateException("submit order failed. status=" + response.getStatusCodeValue());
        }
    }
}
