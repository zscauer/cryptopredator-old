package ru.tyumentsev.cryptopredator.indicatorvirginbot.controller;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.market.Candlestick;
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
import ru.tyumentsev.cryptopredator.commons.domain.BTCTrend;
import ru.tyumentsev.cryptopredator.commons.domain.MonitoredPosition;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.PlacedOrder;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.indicatorvirginbot.cache.IndicatorVirginStrategyCondition;
import ru.tyumentsev.cryptopredator.indicatorvirginbot.strategy.IndicatorVirgin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/indicatorvirgin")
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class IndicatorVirginController {

    MarketInfo marketInfo;
    IndicatorVirginStrategyCondition indicatorVirginStrategyCondition;
    IndicatorVirgin indicatorVirgin;

    @GetMapping("/ping/{pair}")
    public void ping(@PathVariable String pair) {
        indicatorVirginStrategyCondition.ping(pair);
    }

    @GetMapping("/btcTrend")
    public BTCTrend getBtcTrend() {
        return indicatorVirgin.getBtcTrend();
    }

    @GetMapping("/monitoredPositions")
    public List<MonitoredPosition> getMonitoredPositions() {
        return indicatorVirginStrategyCondition.getMonitoredPositions().values().stream()
                .sorted(Comparator.comparingInt(MonitoredPosition::getWeight).reversed().thenComparing(MonitoredPosition::getBeginMonitoringTime))
                .toList();
    }

    @GetMapping("/placedOrders")
    public Map<String, PlacedOrder> getPlacedOrders() {
        return marketInfo.getPlacedOrders();
    }

    @GetMapping("/openedPositions/long")
    public Map<String, List<OpenedPosition>> getOpenedLongPositions() {
        return indicatorVirginStrategyCondition.getLongPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::updateStamp))
                .collect(Collectors.groupingBy(position -> {
                    if (position.isProfitable()) return "Profitable";
                    else return "NOT profitable";
                }));
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
    public Map<String, List<String>> getCandleStickEventsStreams() {
        Map<String, List<String>> response = new HashMap<>();
        response.put("Opened", indicatorVirgin.getOpenedPositionsCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList()));
        response.put("Market", indicatorVirgin.getMarketCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList()));
        return response;
    }

    @GetMapping("/barSeries/market")
    public List<BarSeries> getAllMarketBarSeries() {
        return new ArrayList<>(indicatorVirgin.getMarketBarSeriesMap().values());
    }

    @GetMapping("/barSeries/opened")
    public List<BarSeries> getAllOpenedPositionsBarSeries() {
        return new ArrayList<>(indicatorVirgin.getOpenedPositionsBarSeriesMap().values());
    }

    @GetMapping("/barSeries/market/{symbol}")
    public BarSeries getMarketBarSeries(@PathVariable String symbol) {
        return indicatorVirgin.getMarketBarSeriesMap().get(symbol);
    }

    @GetMapping("/barSeries/opened/{symbol}")
    public BarSeries getOpenedPositionBarSeries(@PathVariable String symbol) {
        return indicatorVirgin.getOpenedPositionsBarSeriesMap().get(symbol);
    }

    @GetMapping("/upperTimeframeCandles")
    public Map<String, List<Candlestick>> getUpperTimeframeCandles() {
        return indicatorVirginStrategyCondition.getUpperTimeframeCandles();
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
