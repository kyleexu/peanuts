package com.ganten.peanuts.maker.cache;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ganten.peanuts.common.enums.Contract;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TickerCache {

	private final Map<Contract, BigDecimal> lastPriceByContract = new ConcurrentHashMap<Contract, BigDecimal>();

	public void putLastPrice(Contract contract, BigDecimal lastPrice) {
		if (contract == null || lastPrice == null || lastPrice.signum() <= 0) {
			return;
		}
		lastPriceByContract.put(contract, lastPrice);
	}

	public BigDecimal getLastPrice(Contract contract) {
		return lastPriceByContract.get(contract);
	}

	public Map<Contract, BigDecimal> snapshot() {
		return Collections.unmodifiableMap(new ConcurrentHashMap<Contract, BigDecimal>(lastPriceByContract));
	}
}
