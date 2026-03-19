package com.ganten.peanuts.market.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.market.model.CandleInterval;
import com.ganten.peanuts.market.model.CandleSnapshot;
import com.ganten.peanuts.market.model.OrderBookSnapshot;
import com.ganten.peanuts.market.model.TickerSnapshot;
import com.ganten.peanuts.market.service.MarketDataService;
import com.ganten.peanuts.market.service.OrderBookAggregationService;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final OrderBookAggregationService orderBookAggregationService;

    public MarketDataController(MarketDataService marketDataService,
            OrderBookAggregationService orderBookAggregationService) {
        this.marketDataService = marketDataService;
        this.orderBookAggregationService = orderBookAggregationService;
    }

    @GetMapping("/ticker/{contract}")
    public TickerSnapshot ticker(@PathVariable Contract contract) {
        return marketDataService.ticker(contract);
    }

    @GetMapping("/candles/{contract}")
    public List<CandleSnapshot> candles(@PathVariable Contract contract,
            @RequestParam(name = "interval", defaultValue = "1m") String interval,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return marketDataService.candles(contract, CandleInterval.fromCode(interval), limit);
    }

    @GetMapping("/orderbook/{contract}")
    public OrderBookSnapshot orderBook(@PathVariable Contract contract,
            @RequestParam(name = "level", defaultValue = "1") int level) {
        return orderBookAggregationService.orderBook(contract, level);
    }
}
