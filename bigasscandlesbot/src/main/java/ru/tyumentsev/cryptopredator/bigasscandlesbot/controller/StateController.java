package ru.tyumentsev.cryptopredator.bigasscandlesbot.controller;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.BarSeries;
import ru.tyumentsev.cryptopredator.bigasscandlesbot.cache.BigAssCandlesStrategyCondition;
import ru.tyumentsev.cryptopredator.bigasscandlesbot.strategy.BigAssCandles;
import ru.tyumentsev.cryptopredator.commons.domain.BTCTrend;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.PlacedOrder;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/state")
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class StateController {

    BigAssCandlesStrategyCondition bigAssCandlesStrategyCondition;
    MarketInfo marketInfo;
    BigAssCandles bigAssCandles;

    @GetMapping("/btcTrend")
    public Map<BTCTrend, Boolean> getBtcTrend() {
        return Collections.singletonMap(bigAssCandles.getBtcTrend(), bigAssCandles.getBtcTrend().isBullish());
    }

    @GetMapping("/monitoredPositions")
    public List<BigAssCandlesStrategyCondition.MonitoredPosition> getMonitoredPositions() {
        return bigAssCandlesStrategyCondition.getMonitoredPositions().values().stream()
                .sorted(Comparator.comparing(BigAssCandlesStrategyCondition.MonitoredPosition::beginMonitoringTime))
                .toList();
    }

    @GetMapping("/placedOrders")
    public Map<String, PlacedOrder> getPlacedOrders() {
        return marketInfo.getPlacedOrders();
    }

    @GetMapping("/openedPositions/long")
    public List<OpenedPosition> getOpenedLongPositions() {
        return bigAssCandlesStrategyCondition.getLongPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::updateStamp))
                .collect(Collectors.toList());
    }

    @GetMapping("/openedPositions/short")
    public List<OpenedPosition> getOpenedShortPositions() {
        return bigAssCandlesStrategyCondition.getShortPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::updateStamp))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/openedPositions/long/{pair}")
    public void deletePairFromOpenedLongPositionsCache(@PathVariable String pair) {
        bigAssCandlesStrategyCondition.getLongPositions().remove(pair.toUpperCase());
    }

    @GetMapping("/sellJournal")
    public List<SellRecord> getSellJournal() {
        return bigAssCandlesStrategyCondition.getSellJournal().values().stream()
                .sorted(Comparator.comparing(SellRecord::sellTime).reversed())
                .collect(Collectors.toList());
    }

    @GetMapping("/candleStickEventsStreams")
    public Map<String, List<String>> getCandleStickEventsStreams() {
        Map<String, List<String>> response = new HashMap<>();
        response.put("Opened", bigAssCandles.getOpenedPositionsCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList()));
        response.put("Market", bigAssCandles.getMarketCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList()));
        return response;
    }

    @GetMapping("/barSeries/market")
    public List<BarSeries> getAllMarketBarSeries() {
        return new ArrayList<>(bigAssCandles.getMarketBarSeriesMap().values());
    }

    @GetMapping("/barSeries/opened")
    public List<BarSeries> getAllOpenedPositionsBarSeries() {
        return new ArrayList<>(bigAssCandles.getOpenedPositionsBarSeriesMap().values());
    }

    @GetMapping("/barSeries/market/{symbol}")
    public BarSeries getMarketBarSeries(@PathVariable String symbol) {
        return bigAssCandles.getMarketBarSeriesMap().get(symbol);
    }

    @GetMapping("/barSeries/opened/{symbol}")
    public BarSeries getOpenedPositionBarSeries(@PathVariable String symbol) {
        return bigAssCandles.getOpenedPositionsBarSeriesMap().get(symbol);
    }

    @PostMapping("/userDataUpdateEvent")
    public void handleUserDataUpdateEvent(@RequestBody OrderTradeUpdateEvent event) {
        log.debug("Get order trade update event: {}", event);
        switch (event.getSide()) {
            case BUY -> {
                bigAssCandles.handleBuying(event);
            }
            case SELL -> {
                bigAssCandles.handleSelling(event);
            }
        }
    }
}
