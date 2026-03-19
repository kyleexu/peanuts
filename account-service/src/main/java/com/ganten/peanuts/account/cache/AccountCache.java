package com.ganten.peanuts.account.cache;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class AccountCache {

	private final Map<Long, Map<String, AssetBalance>> accountAssets = new ConcurrentHashMap<Long, Map<String, AssetBalance>>();

	public AccountAssetSnapshot query(long userId, String asset) {
		AssetBalance balance = getOrCreateBalance(userId, asset);
		balance.stateLock.lock();
		try {
			return new AccountAssetSnapshot(userId, normalizeAsset(asset), balance.available, balance.locked,
				balance.frozen);
		} finally {
			balance.stateLock.unlock();
		}
	}

	public void increaseAvailable(long userId, String asset, BigDecimal amount) {
		validateAmount(amount, "amount");
		AssetBalance balance = getOrCreateBalance(userId, asset);
		balance.stateLock.lock();
		try {
			balance.available = balance.available.add(amount);
		} finally {
			balance.stateLock.unlock();
		}
	}

	public boolean lock(long userId, String asset, BigDecimal amount) {
		validateAmount(amount, "amount");
		AssetBalance balance = getOrCreateBalance(userId, asset);
		balance.stateLock.lock();
		try {
			if (balance.frozen) {
				return false;
			}
			if (balance.available.compareTo(amount) < 0) {
				return false;
			}
			balance.available = balance.available.subtract(amount);
			balance.locked = balance.locked.add(amount);
			return true;
		} finally {
			balance.stateLock.unlock();
		}
	}

	public boolean deductAvailable(long userId, String asset, BigDecimal amount) {
		validateAmount(amount, "amount");
		AssetBalance balance = getOrCreateBalance(userId, asset);
		balance.stateLock.lock();
		try {
			if (balance.frozen) {
				return false;
			}
			if (balance.available.compareTo(amount) < 0) {
				return false;
			}
			balance.available = balance.available.subtract(amount);
			return true;
		} finally {
			balance.stateLock.unlock();
		}
	}

	public boolean deductLocked(long userId, String asset, BigDecimal amount) {
		validateAmount(amount, "amount");
		AssetBalance balance = getOrCreateBalance(userId, asset);
		balance.stateLock.lock();
		try {
			if (balance.locked.compareTo(amount) < 0) {
				return false;
			}
			balance.locked = balance.locked.subtract(amount);
			return true;
		} finally {
			balance.stateLock.unlock();
		}
	}

	public boolean transferIncrease(long fromUserId, long toUserId, String asset, BigDecimal amount) {
		validateAmount(amount, "amount");
		if (fromUserId == toUserId) {
			return true;
		}

		AssetBalance from = getOrCreateBalance(fromUserId, asset);
		AssetBalance to = getOrCreateBalance(toUserId, asset);

		AssetBalance first = fromUserId <= toUserId ? from : to;
		AssetBalance second = fromUserId <= toUserId ? to : from;

		first.stateLock.lock();
		second.stateLock.lock();
		try {
			if (from.locked.compareTo(amount) < 0) {
				return false;
			}
			from.locked = from.locked.subtract(amount);
			to.available = to.available.add(amount);
			return true;
		} finally {
			second.stateLock.unlock();
			first.stateLock.unlock();
		}
	}

	public void setFrozen(long userId, String asset, boolean frozen) {
		AssetBalance balance = getOrCreateBalance(userId, asset);
		balance.stateLock.lock();
		try {
			balance.frozen = frozen;
		} finally {
			balance.stateLock.unlock();
		}
	}

	public boolean isFrozen(long userId, String asset) {
		AssetBalance balance = getOrCreateBalance(userId, asset);
		balance.stateLock.lock();
		try {
			return balance.frozen;
		} finally {
			balance.stateLock.unlock();
		}
	}

	private AssetBalance getOrCreateBalance(long userId, String asset) {
		Map<String, AssetBalance> userAssets = accountAssets.computeIfAbsent(Long.valueOf(userId),
			k -> new ConcurrentHashMap<String, AssetBalance>());
		String normalizedAsset = normalizeAsset(asset);
		return userAssets.computeIfAbsent(normalizedAsset, k -> new AssetBalance());
	}

	private String normalizeAsset(String asset) {
		if (asset == null || asset.trim().isEmpty()) {
			throw new IllegalArgumentException("asset must not be blank");
		}
		return asset.trim().toUpperCase();
	}

	private void validateAmount(BigDecimal amount, String field) {
		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException(field + " must be positive");
		}
	}

	public static final class AccountAssetSnapshot {

		private final long userId;
		private final String asset;
		private final BigDecimal available;
		private final BigDecimal locked;
		private final boolean frozen;

		public AccountAssetSnapshot(long userId, String asset, BigDecimal available, BigDecimal locked,
				boolean frozen) {
			this.userId = userId;
			this.asset = asset;
			this.available = available;
			this.locked = locked;
			this.frozen = frozen;
		}

		public long getUserId() {
			return userId;
		}

		public String getAsset() {
			return asset;
		}

		public BigDecimal getAvailable() {
			return available;
		}

		public BigDecimal getLocked() {
			return locked;
		}

		public boolean isFrozen() {
			return frozen;
		}
	}

	private static final class AssetBalance {

		private final ReentrantLock stateLock = new ReentrantLock();
		private BigDecimal available = BigDecimal.ZERO;
		private BigDecimal locked = BigDecimal.ZERO;
		private boolean frozen = false;
	}
}
