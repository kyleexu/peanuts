package com.ganten.peanuts.maker.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.common.util.JsonUtils;
import com.ganten.peanuts.maker.constants.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MarketClient {

    private static final String ORDERBOOK_API = "%s/api/market/orderbook/%s?level=%d";
    private static final String TICKER_API = "%s/api/market/ticker/%s";
    private final RestTemplate restTemplate = new RestTemplate();

    public MarketClient() {}

    public BigDecimal resolvePriceByTicker(Contract contract, int orderBookLevel) {
        try {
            BigDecimal lastPrice = fetchTickerLastPrice(contract);
            TopOfBookSnapshot top = fetchTopOfBook(contract, Math.max(1, orderBookLevel));

            if (isPositive(top.bestBid) && isPositive(top.bestAsk)) {
                BigDecimal midPrice =
                        top.bestBid.add(top.bestAsk).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                if (isPositive(lastPrice)) {
                    // Align with Bybit ticker semantics: bid1/ask1 are primary, lastPrice is trade anchor.
                    if (lastPrice.compareTo(top.bestBid) >= 0 && lastPrice.compareTo(top.bestAsk) <= 0) {
                        return lastPrice.setScale(4, RoundingMode.HALF_UP);
                    }
                    return lastPrice.add(midPrice).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                }
                return midPrice;
            }

            if (isPositive(lastPrice)) {
                return lastPrice.setScale(4, RoundingMode.HALF_UP);
            }

            if (isPositive(top.bestAsk)) {
                return top.bestAsk.setScale(4, RoundingMode.HALF_UP);
            }
            if (isPositive(top.bestBid)) {
                return top.bestBid.setScale(4, RoundingMode.HALF_UP);
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    public TopOfBookSnapshot fetchTopOfBook(Contract contract, int orderBookLevel) {
        TopOfBookSnapshot top = new TopOfBookSnapshot();
        try {
            String url =
                    String.format(ORDERBOOK_API, Constants.MARKET_URL, contract.name(), Math.max(1, orderBookLevel));
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return top;
            }
            JsonNode root = JsonUtils.readTree(body);
            top.bestBid = firstPrice(root.path("bids"));
            top.bestAsk = firstPrice(root.path("asks"));
            return top;
        } catch (Exception ex) {
            return top;
        }
    }

    private BigDecimal fetchTickerLastPrice(Contract contract) {
        try {
            String url = String.format(TICKER_API, Constants.MARKET_URL, contract.name());
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isEmpty()) {
                return null;
            }
            JsonNode root = JsonUtils.readTree(body);
            JsonNode lastPriceNode = root.path("lastPrice");
            if (lastPriceNode.isMissingNode() || lastPriceNode.isNull()) {
                return null;
            }
            return new BigDecimal(lastPriceNode.asText("0"));
        } catch (Exception ex) {
            return null;
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

    public static final class TopOfBookSnapshot {
        private BigDecimal bestBid;
        private BigDecimal bestAsk;

        public BigDecimal getBestBid() {
            return bestBid;
        }

        public BigDecimal getBestAsk() {
            return bestAsk;
        }
    }
}
