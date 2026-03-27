package com.ganten.peanuts.maker.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ganten.peanuts.common.enums.Currency;

@Component
public class AccountClient {

    private final String accountApiBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountClient(@Value("${maker.random-order.account-api-base-url:http://localhost:8081}") String accountApiBaseUrl) {
        this.accountApiBaseUrl = accountApiBaseUrl;
    }

    public BigDecimal fetchAvailableBalance(long userId, Currency currency) {
        try {
            String url = String.format("%s/api/accounts/%d/currencies/%s", this.accountApiBaseUrl.trim(), userId,
                    currency.name());
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode available = root.path("available");
            if (available.isMissingNode() || available.isNull()) {
                return null;
            }
            return new BigDecimal(available.asText("0"));
        } catch (Exception ex) {
            return null;
        }
    }
}
