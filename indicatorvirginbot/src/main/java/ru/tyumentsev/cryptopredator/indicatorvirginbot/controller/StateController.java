package ru.tyumentsev.cryptopredator.indicatorvirginbot.controller;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
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
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.PlacedOrder;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.indicatorvirginbot.cache.IndicatorVirginStrategyCondition;
import ru.tyumentsev.cryptopredator.indicatorvirginbot.strategy.IndicatorVirgin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/state")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class StateController {

    IndicatorVirginStrategyCondition indicatorVirginStrategyCondition;
    MarketInfo marketInfo;
    IndicatorVirgin indicatorVirgin;

    @GetMapping("/monitoredPositions")
    public Map<String, IndicatorVirginStrategyCondition.MonitoredPosition> getMonitoredPositions() {
        return indicatorVirginStrategyCondition.getMonitoredPositions();
    }

    @GetMapping("/placedOrders")
    public Map<String, PlacedOrder> getPlacedOrders() {
        return marketInfo.getPlacedOrders();
    }

    @GetMapping("/openedPositions/long")
    public List<OpenedPosition> getOpenedLongPositions() {
        return indicatorVirginStrategyCondition.getLongPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::updateStamp))
                .collect(Collectors.toList());
    }

    @GetMapping("/openedPositions/short")
    public List<OpenedPosition> getOpenedShortPositions() {
        return indicatorVirginStrategyCondition.getShortPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::updateStamp))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/openedPositions/long/{pair}")
    public void deletePairFromOpenedLongPositionsCache(@PathVariable String pair) {
        indicatorVirginStrategyCondition.getLongPositions().remove(pair.toUpperCase());
    }

    @GetMapping("/sellJournal")
    public List<SellRecord> getSellJournal() {
        return indicatorVirginStrategyCondition.getSellJournal().values().stream()
                .sorted(Comparator.comparing(SellRecord::sellTime).reversed())
                .collect(Collectors.toList());
    }

    @GetMapping("/candleStickEventsStreams")
    public List<String> getDailyCandleStickEventsStreams() {
        return indicatorVirgin.getCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    @GetMapping("/barSeries")
    public List<BarSeries> getAllBarSeries() {
        return new ArrayList<>(indicatorVirgin.getBarSeriesMap().values());
    }

    @GetMapping("/barSeries/{symbol}")
    public BarSeries getBarSeries(@PathVariable String symbol) {
        return indicatorVirgin.getBarSeriesMap().get(symbol);
    }

    @PostMapping("/userDataUpdateEvent")
    public void handleUserDataUpdateEvent(@RequestBody OrderTradeUpdateEvent event) {
        log.debug("Get order trade update event: {}", event);
        switch (event.getSide()) {
            case BUY -> {
                indicatorVirgin.handleBuying(event);
            }
            case SELL -> {
                indicatorVirgin.handleSelling(event);
            }
        }
    }
}
