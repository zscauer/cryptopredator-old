package ru.tyumentsev.cryptopredator.volumenimblebot.controller;

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
import ru.tyumentsev.cryptopredator.commons.cache.CandleSeries;
import ru.tyumentsev.cryptopredator.commons.domain.BTCTrend;
import ru.tyumentsev.cryptopredator.commons.domain.MonitoredPosition;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.PlacedOrder;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.volumenimblebot.cache.VolumeNimbleStrategyCondition;
import ru.tyumentsev.cryptopredator.volumenimblebot.strategy.VolumeNimble;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/volumenimble")
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class VolumeNimbleController {

    MarketInfo marketInfo;
    VolumeNimbleStrategyCondition volumeNimbleStrategyCondition;
    VolumeNimble volumeNimble;

    @GetMapping("/ping/{pair}")
    public void ping(@PathVariable String pair) {
        volumeNimbleStrategyCondition.ping(pair);
    }

    @GetMapping("/btcTrend")
    public BTCTrend getBtcTrend() {
        return volumeNimble.getBtcTrend();
    }

    @GetMapping("/monitoredPositions")
    public List<MonitoredPosition> getMonitoredPositions() {
        return volumeNimbleStrategyCondition.getMonitoredPositions().values().stream()
                .sorted(Comparator.comparingInt(MonitoredPosition::getWeight).reversed().thenComparing(MonitoredPosition::getBeginMonitoringTime))
                .toList();
    }

    @GetMapping("/candleSeries/{symbol}")
    public CandleSeries getCandleSeries(@PathVariable String symbol) {
        return volumeNimble.getCandleSeriesMap().get(symbol);
    }

    @GetMapping("/placedOrders")
    public Map<String, PlacedOrder> getPlacedOrders() {
        return marketInfo.getPlacedOrders();
    }

    @GetMapping("/openedPositions/long")
    public Map<String, List<OpenedPosition>> getOpenedLongPositions() {
        return volumeNimbleStrategyCondition.getLongPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::updateStamp))
                .collect(Collectors.groupingBy(position -> {
                    if (position.isProfitable()) return "Profitable";
                    else return "NOT profitable";
                }));
    }

    @GetMapping("/openedPositions/short")
    public List<OpenedPosition> getOpenedShortPositions() {
        return volumeNimbleStrategyCondition.getShortPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::updateStamp))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/openedPositions/long/{pair}")
    public void deletePairFromOpenedLongPositionsCache(@PathVariable String pair) {
        volumeNimbleStrategyCondition.getLongPositions().remove(pair.toUpperCase());
    }

    @GetMapping("/sellJournal")
    public List<SellRecord> getSellJournal() {
        return volumeNimbleStrategyCondition.getSellJournal().values().stream()
                .sorted(Comparator.comparing(SellRecord::sellTime).reversed())
                .collect(Collectors.toList());
    }

    @GetMapping("/candleStickEventsStreams")
    public Map<String, List<String>> getCandleStickEventsStreams() {
        Map<String, List<String>> response = new HashMap<>();
        response.put("Opened", volumeNimble.getOpenedPositionsCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList()));
        response.put("Market", volumeNimble.getMarketCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList()));
        return response;
    }

    @GetMapping("/barSeries/market")
    public List<BarSeries> getAllMarketBarSeries() {
        return new ArrayList<>(volumeNimble.getMarketBarSeriesMap().values());
    }

    @GetMapping("/barSeries/opened")
    public List<BarSeries> getAllOpenedPositionsBarSeries() {
        return new ArrayList<>(volumeNimble.getOpenedPositionsBarSeriesMap().values());
    }

    @GetMapping("/barSeries/market/{symbol}")
    public BarSeries getMarketBarSeries(@PathVariable String symbol) {
        return volumeNimble.getMarketBarSeriesMap().get(symbol);
    }

    @GetMapping("/barSeries/opened/{symbol}")
    public BarSeries getOpenedPositionBarSeries(@PathVariable String symbol) {
        return volumeNimble.getOpenedPositionsBarSeriesMap().get(symbol);
    }

    @GetMapping("/upperTimeframeCandles")
    public Map<String, List<Candlestick>> getUpperTimeframeCandles() {
        return volumeNimbleStrategyCondition.getUpperTimeframeCandles();
    }

    @PostMapping("/userDataUpdateEvent")
    public void handleUserDataUpdateEvent(@RequestBody OrderTradeUpdateEvent event) {
        log.debug("Get order trade update event: {}", event);
        switch (event.getSide()) {
            case BUY -> {
                volumeNimble.handleBuying(event);
            }
            case SELL -> {
                volumeNimble.handleSelling(event);
            }
        }
    }
}
