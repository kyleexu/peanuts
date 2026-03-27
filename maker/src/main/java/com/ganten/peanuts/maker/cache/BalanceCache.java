package com.ganten.peanuts.maker.cache;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.enums.Currency;
import com.ganten.peanuts.maker.client.AccountClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BalanceCache {

    private final AccountClient accountClient;
    private final long warnIntervalMs;
    private final Map<String, TrackedBalance> balances = new ConcurrentHashMap<String, TrackedBalance>();
    private final Map<String, Long> warnAt = new ConcurrentHashMap<String, Long>();

    public BalanceCache(AccountClient accountClient,
            @Value("${maker.random-order.balance-warn-interval-ms:30000}") long warnIntervalMs) {
        this.accountClient = accountClient;
        this.warnIntervalMs = Math.max(1000L, warnIntervalMs);
    }

    public void register(long userId, Currency currency, BigDecimal initialValue) {
        String key = key(userId, currency);
        balances.computeIfAbsent(key,
                k -> new TrackedBalance(userId, currency, initialValue == null ? BigDecimal.ZERO : initialValue));
    }

    public BigDecimal getAvailableBalance(long userId, Currency currency) {
        TrackedBalance tracked = balances.get(key(userId, currency));
        return tracked == null ? null : tracked.available;
    }

    public void putAvailableBalance(long userId, Currency currency, BigDecimal available) {
        if (available == null) {
            return;
        }
        String k = key(userId, currency);
        balances.compute(k, (unused, existing) -> {
            if (existing == null) {
                return new TrackedBalance(userId, currency, available);
            }
            existing.available = available;
            return existing;
        });
    }

    @Scheduled(fixedDelayString = "${maker.random-order.balance-cache-sync-fixed-delay-ms:1000}")
    public void syncFromAccountClient() {
        for (Map.Entry<String, TrackedBalance> entry : balances.entrySet()) {
            TrackedBalance tracked = entry.getValue();
            BigDecimal fetched = accountClient.fetchAvailableBalance(tracked.userId, tracked.currency);
            if (fetched != null) {
                tracked.available = fetched;
                continue;
            }
            warnFallback(entry.getKey(), tracked.userId, tracked.currency, tracked.available);
        }
    }

    private void warnFallback(String cacheKey, long userId, Currency currency, BigDecimal cached) {
        long now = System.currentTimeMillis();
        Long last = warnAt.get(cacheKey);
        if (last == null || now - last.longValue() >= warnIntervalMs) {
            warnAt.put(cacheKey, now);
            log.warn("Read account balance failed, fallback cache. userId={}, currency={}, cached={}", userId, currency,
                    cached);
        }
    }

    private String key(long userId, Currency currency) {
        return userId + ":" + currency.name();
    }

    private static final class TrackedBalance {
        private final long userId;
        private final Currency currency;
        private volatile BigDecimal available;

        private TrackedBalance(long userId, Currency currency, BigDecimal available) {
            this.userId = userId;
            this.currency = currency;
            this.available = available;
        }
    }
}
