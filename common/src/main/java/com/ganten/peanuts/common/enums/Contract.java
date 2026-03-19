package com.ganten.peanuts.common.enums;

import lombok.Getter;

@Getter
public enum Contract {
    // btc/usdt
    BTC_USDT(1L, "BTC", "USDT", "BTCUSDT", 0.01, 1),
    // eth/usdt
    ETH_USDT(2L, "ETH", "USDT", "ETHUSDT", 0.01, 1),
    // usdt/usd
    USDT_USD(3L, "USDT", "USD", "USDTUSD", 0.0001, 1);

    private Long id;
    private String base;
    private String quote;
    private String symbol;
    private double tickSize;
    private double lotSize;

    Contract(Long id, String base, String quote, String symbol, double tickSize, double lotSize) {
        this.id = id;
        this.base = base;
        this.quote = quote;
        this.symbol = symbol;
        this.tickSize = tickSize;
        this.lotSize = lotSize;
    }

    public static Contract getContractById(Long id) {
        for (Contract contract : Contract.values()) {
            if (contract.getId().equals(id)) {
                return contract;
            }
        }
        return null;
    }

    public static Contract fromString(String name) {
        for (Contract contract : Contract.values()) {
            if (contract.name().equalsIgnoreCase(name)) {
                return contract;
            }
        }
        return null;
    }

    public static Long getContractIdBySymbol(String symbol) {
        for (Contract contract : Contract.values()) {
            if (contract.getSymbol().equalsIgnoreCase(symbol)) {
                return contract.getId();
            }
        }
        return null;
    }


    public static Contract getContractBySymbol(String symbol) {
        for (Contract contract : Contract.values()) {
            if (contract.getSymbol().equalsIgnoreCase(symbol)) {
                return contract;
            }
        }
        return null;
    }
}
