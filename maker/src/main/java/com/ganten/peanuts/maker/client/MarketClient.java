package com.ganten.peanuts.maker.client;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ganten.peanuts.common.enums.Contract;

@Component
public class MarketClient {

    private final String marketApiBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketClient(@Value("${maker.random-order.market-api-base-url:http://localhost:8082}") String marketApiBaseUrl) {
        this.marketApiBaseUrl = marketApiBaseUrl;
    }

    public BigDecimal resolvePriceByTicker(Contract contract, int orderBookLevel) {
        try {
            BigDecimal lastPrice = fetchTickerLastPrice(contract);
            TopOfBook top = fetchTopOfBook(contract, Math.max(1, orderBookLevel));

            if (isPositive(top.bid1Price) && isPositive(top.ask1Price)) {
                BigDecimal midPrice = top.bid1Price.add(top.ask1Price)
                        .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                if (isPositive(lastPrice)) {
                    // Align with Bybit ticker semantics: bid1/ask1 are primary, lastPrice is trade anchor.
                    if (lastPrice.compareTo(top.bid1Price) >= 0 && lastPrice.compareTo(top.ask1Price) <= 0) {
                        return lastPrice.setScale(4, RoundingMode.HALF_UP);
                    }
                    return lastPrice.add(midPrice).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                }
                return midPrice;
            }

            if (isPositive(lastPrice)) {
                return lastPrice.setScale(4, RoundingMode.HALF_UP);
            }

            if (isPositive(top.ask1Price)) {
                return top.ask1Price.setScale(4, RoundingMode.HALF_UP);
            }
            if (isPositive(top.bid1Price)) {
                return top.bid1Price.setScale(4, RoundingMode.HALF_UP);
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal fetchTickerLastPrice(Contract contract) {
        try {
            String url = String.format("%s/api/market/ticker/%s", this.marketApiBaseUrl, contract.name());
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode lastPriceNode = root.path("lastPrice");
            if (lastPriceNode.isMissingNode() || lastPriceNode.isNull()) {
                return null;
            }
            return new BigDecimal(lastPriceNode.asText("0"));
        } catch (Exception ex) {
            return null;
        }
    }

    private TopOfBook fetchTopOfBook(Contract contract, int orderBookLevel) {
        TopOfBook top = new TopOfBook();
        try {
            String url = String.format("%s/api/market/orderbook/%s?level=%d", this.marketApiBaseUrl,
                    contract.name(), orderBookLevel);
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return top;
            }
            JsonNode root = objectMapper.readTree(body);
            top.bid1Price = firstPrice(root.path("bids"));
            top.ask1Price = firstPrice(root.path("asks"));
            return top;
        } catch (Exception ex) {
            return top;
        }
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private BigDecimal firstPrice(JsonNode levels) {
        if (levels == null || !levels.isArray() || levels.size() == 0) {
            return null;
        }
        JsonNode first = levels.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        JsonNode priceNode = first.path("price");
        if (priceNode.isMissingNode() || priceNode.isNull()) {
            return null;
        }
        return new BigDecimal(priceNode.asText("0"));
    }

    private static final class TopOfBook {
        private BigDecimal bid1Price;
        private BigDecimal ask1Price;
    }
}
