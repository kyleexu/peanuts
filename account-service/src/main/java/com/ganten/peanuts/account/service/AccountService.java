package com.ganten.peanuts.account.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.account.cache.AccountCache;
import com.ganten.peanuts.account.cache.AccountCache.AccountAssetSnapshot;

@Service
public class AccountService {

	private final AccountCache accountCache;

	public AccountService(AccountCache accountCache) {
		this.accountCache = accountCache;
	}

	public AccountAssetSnapshot query(long userId, String asset) {
		return accountCache.query(userId, asset);
	}

	public void increaseAvailable(long userId, String asset, BigDecimal amount) {
		accountCache.increaseAvailable(userId, asset, amount);
	}

	public boolean lock(long userId, String asset, BigDecimal amount) {
		return accountCache.lock(userId, asset, amount);
	}

	public boolean deductAvailable(long userId, String asset, BigDecimal amount) {
		return accountCache.deductAvailable(userId, asset, amount);
	}

	public boolean deductLocked(long userId, String asset, BigDecimal amount) {
		return accountCache.deductLocked(userId, asset, amount);
	}

	public boolean transferIncrease(long fromUserId, long toUserId, String asset, BigDecimal amount) {
		return accountCache.transferIncrease(fromUserId, toUserId, asset, amount);
	}

	public void setFrozen(long userId, String asset, boolean frozen) {
		accountCache.setFrozen(userId, asset, frozen);
	}

	public boolean isFrozen(long userId, String asset) {
		return accountCache.isFrozen(userId, asset);
	}
}
