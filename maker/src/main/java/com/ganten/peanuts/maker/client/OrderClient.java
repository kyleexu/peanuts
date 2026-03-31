package com.ganten.peanuts.maker.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.ganten.peanuts.maker.constants.Constants;
import com.ganten.peanuts.maker.model.OrderSubmitRequest;

@Component
public class OrderClient {

    private final RestTemplate restTemplate = new RestTemplate();

    public void submitOrder(OrderSubmitRequest order) {
        String url = Constants.ORDER_URL.trim() + "/api/orders";
        HttpEntity<OrderSubmitRequest> requestEntity = new HttpEntity<OrderSubmitRequest>(order);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode().value() != 202) {
            throw new IllegalStateException("submit order failed. status=" + response.getStatusCodeValue());
        }
    }
}
