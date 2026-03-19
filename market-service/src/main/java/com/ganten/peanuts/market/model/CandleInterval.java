package com.ganten.peanuts.market.model;

public enum CandleInterval {
    M1("1m", 60_000L), M5("5m", 5 * 60_000L), M15("15m", 15 * 60_000L), H1("1h", 60 * 60_000L), D1("1d",
            24 * 60 * 60_000L);

    private final String code;
    private final long millis;

    CandleInterval(String code, long millis) {
        this.code = code;
        this.millis = millis;
    }

    public String getCode() {
        return code;
    }

    public long getMillis() {
        return millis;
    }

    public static CandleInterval fromCode(String code) {
        if (code == null) {
            return M1;
        }
        for (CandleInterval interval : values()) {
            if (interval.code.equalsIgnoreCase(code)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("unsupported interval: " + code);
    }
}
