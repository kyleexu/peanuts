package com.ganten.peanuts.account.cache;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import com.ganten.peanuts.common.entity.AccountAssetSnapshot;
import com.ganten.peanuts.common.enums.Currency;

@Service
public class AccountCache {

	private final Map<Long, UserBalance> accountAssets = new ConcurrentHashMap<Long, UserBalance>();

	public AccountAssetSnapshot query(long userId, Currency currency) {
		UserBalance userBalance = getOrCreateBalance(userId);
		Currency normalizedCurrency = requireCurrency(currency);
		synchronized (userBalance) {
			return new AccountAssetSnapshot(userId, normalizedCurrency,
					userBalance.available.getOrDefault(normalizedCurrency, BigDecimal.ZERO),
					userBalance.locked.getOrDefault(normalizedCurrency, BigDecimal.ZERO));
		}
	}

	public void increaseAvailable(long userId, Currency currency, BigDecimal amount) {
		validateAmount(amount, "amount");
		UserBalance userBalance = getOrCreateBalance(userId);
		Currency normalizedCurrency = requireCurrency(currency);
		synchronized (userBalance) {
			BigDecimal current = userBalance.available.getOrDefault(normalizedCurrency, BigDecimal.ZERO);
			userBalance.available.put(normalizedCurrency, current.add(amount));
		}
	}

	public boolean lock(long userId, Currency currency, BigDecimal amount) {
		validateAmount(amount, "amount");
		UserBalance userBalance = getOrCreateBalance(userId);
		Currency normalizedCurrency = requireCurrency(currency);
		synchronized (userBalance) {
			BigDecimal available = userBalance.available.getOrDefault(normalizedCurrency, BigDecimal.ZERO);
			if (available.compareTo(amount) < 0) {
				return false;
			}
			BigDecimal locked = userBalance.locked.getOrDefault(normalizedCurrency, BigDecimal.ZERO);
			userBalance.available.put(normalizedCurrency, available.subtract(amount));
			userBalance.locked.put(normalizedCurrency, locked.add(amount));
			return true;
		}
	}

	public boolean deductAvailable(long userId, Currency currency, BigDecimal amount) {
		validateAmount(amount, "amount");
		UserBalance userBalance = getOrCreateBalance(userId);
		Currency normalizedCurrency = requireCurrency(currency);
		synchronized (userBalance) {
			BigDecimal available = userBalance.available.getOrDefault(normalizedCurrency, BigDecimal.ZERO);
			if (available.compareTo(amount) < 0) {
				return false;
			}
			userBalance.available.put(normalizedCurrency, available.subtract(amount));
			return true;
		}
	}

	public boolean deductLocked(long userId, Currency currency, BigDecimal amount) {
		validateAmount(amount, "amount");
		UserBalance userBalance = getOrCreateBalance(userId);
		Currency normalizedCurrency = requireCurrency(currency);
		synchronized (userBalance) {
			BigDecimal locked = userBalance.locked.getOrDefault(normalizedCurrency, BigDecimal.ZERO);
			if (locked.compareTo(amount) < 0) {
				return false;
			}
			userBalance.locked.put(normalizedCurrency, locked.subtract(amount));
			return true;
		}
	}

	public boolean transferIncrease(long fromUserId, long toUserId, Currency currency, BigDecimal amount) {
		validateAmount(amount, "amount");
		if (fromUserId == toUserId) {
			return true;
		}

		UserBalance from = getOrCreateBalance(fromUserId);
		UserBalance to = getOrCreateBalance(toUserId);
		Currency normalizedCurrency = requireCurrency(currency);

		UserBalance first = fromUserId <= toUserId ? from : to;
		UserBalance second = fromUserId <= toUserId ? to : from;

		synchronized (first) {
			synchronized (second) {
				BigDecimal fromLocked = from.locked.getOrDefault(normalizedCurrency, BigDecimal.ZERO);
				if (fromLocked.compareTo(amount) < 0) {
					return false;
				}
				BigDecimal toAvailable = to.available.getOrDefault(normalizedCurrency, BigDecimal.ZERO);
				from.locked.put(normalizedCurrency, fromLocked.subtract(amount));
				to.available.put(normalizedCurrency, toAvailable.add(amount));
				return true;
			}
		}
	}

	public boolean settleTrade(long buyUserId, long sellUserId, Currency baseCurrency, Currency quoteCurrency,
			BigDecimal price, BigDecimal quantity) {
		validateAmount(price, "price");
		validateAmount(quantity, "quantity");
		Currency base = requireCurrency(baseCurrency);
		Currency quote = requireCurrency(quoteCurrency);
		BigDecimal notional = price.multiply(quantity);

		UserBalance buy = getOrCreateBalance(buyUserId);
		UserBalance sell = getOrCreateBalance(sellUserId);
		UserBalance first = buyUserId <= sellUserId ? buy : sell;
		UserBalance second = buyUserId <= sellUserId ? sell : buy;

		synchronized (first) {
			synchronized (second) {
				BigDecimal buyLockedQuote = buy.locked.getOrDefault(quote, BigDecimal.ZERO);
				BigDecimal sellLockedBase = sell.locked.getOrDefault(base, BigDecimal.ZERO);
				if (buyLockedQuote.compareTo(notional) < 0 || sellLockedBase.compareTo(quantity) < 0) {
					return false;
				}

				buy.locked.put(quote, buyLockedQuote.subtract(notional));
				BigDecimal buyAvailableBase = buy.available.getOrDefault(base, BigDecimal.ZERO);
				buy.available.put(base, buyAvailableBase.add(quantity));

				sell.locked.put(base, sellLockedBase.subtract(quantity));
				BigDecimal sellAvailableQuote = sell.available.getOrDefault(quote, BigDecimal.ZERO);
				sell.available.put(quote, sellAvailableQuote.add(notional));
				return true;
			}
		}
	}

	private UserBalance getOrCreateBalance(long userId) {
		return accountAssets.computeIfAbsent(Long.valueOf(userId), k -> new UserBalance());
	}

	private Currency requireCurrency(Currency currency) {
		if (currency == null) {
			throw new IllegalArgumentException("currency must not be null");
		}
		return currency;
	}

	private void validateAmount(BigDecimal amount, String fieldName) {
		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException(fieldName + " must be positive");
		}
	}

	private static final class UserBalance {
		private final Map<Currency, BigDecimal> available = new EnumMap<Currency, BigDecimal>(Currency.class);
		private final Map<Currency, BigDecimal> locked = new EnumMap<Currency, BigDecimal>(Currency.class);
	}
}
