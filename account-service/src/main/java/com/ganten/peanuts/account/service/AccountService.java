package com.ganten.peanuts.account.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.account.cache.AccountCache;
import com.ganten.peanuts.common.entity.AccountAssetSnapshot;
import com.ganten.peanuts.common.enums.Currency;

@Service
public class AccountService {

	private final AccountCache accountCache;

	public AccountService(AccountCache accountCache) {
		this.accountCache = accountCache;
	}

	public AccountAssetSnapshot query(long userId, Currency currency) {
		return accountCache.query(userId, currency);
	}

	public void increaseAvailable(long userId, Currency currency, BigDecimal amount) {
		accountCache.increaseAvailable(userId, currency, amount);
	}

	public boolean tryLock(long userId, Currency currency, BigDecimal amount) {
		return accountCache.lock(userId, currency, amount);
	}

	public boolean deductAvailable(long userId, Currency currency, BigDecimal amount) {
		return accountCache.deductAvailable(userId, currency, amount);
	}

	public boolean deductLocked(long userId, Currency currency, BigDecimal amount) {
		return accountCache.deductLocked(userId, currency, amount);
	}

	public boolean transferIncrease(long fromUserId, long toUserId, Currency currency, BigDecimal amount) {
		return accountCache.transferIncrease(fromUserId, toUserId, currency, amount);
	}
}
