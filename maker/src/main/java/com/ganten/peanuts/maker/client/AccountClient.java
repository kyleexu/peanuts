package com.ganten.peanuts.maker.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ganten.peanuts.common.enums.Currency;

@Component
public class AccountClient {

    private final String accountApiBaseUrl;
    private final long warnIntervalMs;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Long> warnAt = new ConcurrentHashMap<String, Long>();

    public AccountClient(
            @Value("${maker.random-order.account-api-base-url:http://localhost:8081}") String accountApiBaseUrl,
            @Value("${maker.random-order.balance-client-warn-interval-ms:30000}") long warnIntervalMs) {
        this.accountApiBaseUrl = accountApiBaseUrl;
        this.warnIntervalMs = Math.max(1000L, warnIntervalMs);
    }

    public BigDecimal fetchAvailableBalance(long userId, Currency currency) {
        String warnKey = userId + ":" + currency.name();
        try {
            String url = String.format("%s/api/accounts/%d/currencies/%s", this.accountApiBaseUrl.trim(), userId,
                    currency.name());
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                warn(warnKey, "Account API empty response. url=" + url);
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode available = root.path("available");
            if (available.isMissingNode() || available.isNull()) {
                warn(warnKey, "Account API missing available field. url=" + url + ", body=" + brief(body));
                return null;
            }
            return new BigDecimal(available.asText("0"));
        } catch (Exception ex) {
            warn(warnKey, "Account API request failed. baseUrl=" + this.accountApiBaseUrl + ", error="
                    + ex.getMessage());
            return null;
        }
    }

    private void warn(String key, String message) {
        long now = System.currentTimeMillis();
        Long last = warnAt.get(key);
        if (last == null || now - last.longValue() >= warnIntervalMs) {
            warnAt.put(key, now);
            // Avoid per-tick log storms while keeping root cause visible.
            org.slf4j.LoggerFactory.getLogger(AccountClient.class).warn(message);
        }
    }

    private String brief(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= 160) {
            return text;
        }
        return text.substring(0, 160) + "...";
    }
}
