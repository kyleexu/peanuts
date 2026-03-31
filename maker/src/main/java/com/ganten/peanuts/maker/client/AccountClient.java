package com.ganten.peanuts.maker.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.ganten.peanuts.common.enums.Currency;
import com.ganten.peanuts.common.util.JsonUtils;
import com.ganten.peanuts.maker.constants.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AccountClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Long> warnAt = new ConcurrentHashMap<String, Long>();

    public AccountClient() {}

    public BigDecimal fetchAvailableBalance(long userId, Currency currency) {
        String warnKey = userId + ":" + currency.name();
        try {
            String url =
                    String.format("%s/api/accounts/%d/currencies/%s", Constants.ACCOUNT_URL, userId, currency.name());
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                warn(warnKey, "Account API empty response. url=" + url);
                return null;
            }
            JsonNode root = JsonUtils.readTree(body);
            JsonNode available = root.path("available");
            if (available.isMissingNode() || available.isNull()) {
                warn(warnKey, "Account API missing available field. url=" + url + ", body=" + brief(body));
                return null;
            }
            return new BigDecimal(available.asText("0"));
        } catch (Exception ex) {
            warn(warnKey,
                    "Account API request failed. baseUrl=" + Constants.ACCOUNT_URL + ", error=" + ex.getMessage());
            return null;
        }
    }

    private void warn(String key, String message) {
        long now = System.currentTimeMillis();
        Long last = warnAt.get(key);
        if (last == null || now - last.longValue() >= Constants.BALANCE_CLIENT_WARN_INTERVAL_MS) {
            warnAt.put(key, now);
            // Avoid per-tick log storms while keeping root cause visible.
            log.warn(message);
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
