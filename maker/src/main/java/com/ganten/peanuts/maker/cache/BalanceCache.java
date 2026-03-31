package com.ganten.peanuts.maker.cache;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.enums.Currency;
import com.ganten.peanuts.maker.client.AccountClient;
import com.ganten.peanuts.maker.constants.Constants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BalanceCache {

    private final AccountClient accountClient;
    private final Map<String, TrackedBalance> balances = new ConcurrentHashMap<String, TrackedBalance>();

    public BalanceCache(AccountClient accountClient) {
        this.accountClient = accountClient;
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

    @Scheduled(fixedDelay = Constants.BALANCE_CACHE_SYNC_DELAY_MS)
    public void syncFromAccountClient() {
        for (Map.Entry<String, TrackedBalance> entry : balances.entrySet()) {
            TrackedBalance tracked = entry.getValue();
            BigDecimal fetched = accountClient.fetchAvailableBalance(tracked.getUserId(), tracked.getCurrency());
            if (fetched != null) {
                tracked.setAvailable(fetched);
            }
        }
    }

    private String key(long userId, Currency currency) {
        return userId + ":" + currency.name();
    }

    @Data
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
