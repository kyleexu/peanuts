package com.ganten.peanuts.market.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.ganten.peanuts.common.enums.CandleInterval;
import com.ganten.peanuts.common.enums.Contract;
import com.ganten.peanuts.market.model.CandleSnapshot;
import com.ganten.peanuts.market.model.OrderBookSnapshot;
import com.ganten.peanuts.market.model.TickerSnapshot;
import com.ganten.peanuts.market.service.CandleService;
import com.ganten.peanuts.market.service.TickerService;
import com.ganten.peanuts.market.service.OrderBookService;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    private final TickerService tickerService;
    private final CandleService candleService;
    private final OrderBookService orderBookAggregationService;

    public MarketDataController(TickerService tickerService,
            CandleService candleService,
            OrderBookService orderBookAggregationService) {
        this.tickerService = tickerService;
        this.candleService = candleService;
        this.orderBookAggregationService = orderBookAggregationService;
    }

    @GetMapping("/ticker/{contract}")
    public TickerSnapshot ticker(@PathVariable Contract contract) {
        return tickerService.ticker(contract);
    }

    @GetMapping("/candles/{contract}")
    public List<CandleSnapshot> candles(@PathVariable Contract contract,
            @RequestParam(name = "interval", defaultValue = "1m") String interval,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return candleService.candles(contract, CandleInterval.fromCode(interval), limit);
    }

    @GetMapping("/orderbook/{contract}")
    public OrderBookSnapshot orderBook(@PathVariable Contract contract,
            @RequestParam(name = "level", defaultValue = "1") int level) {
        return orderBookAggregationService.orderBook(contract, level);
    }
}
