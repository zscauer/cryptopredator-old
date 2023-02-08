package ru.tyumentsev.cryptopredator.dailyvolumesbot.controller;

import java.util.*;
import java.util.stream.Collectors;

import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.PlacedOrder;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.cache.DailyVolumesStrategyCondition;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.strategy.Daily;

@RestController
@RequestMapping("/state")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class StateController {

    DailyVolumesStrategyCondition dailyVolumesStrategyCondition;
    MarketInfo marketInfo;
    Daily daily;

    @GetMapping("/placedOrders")
    public Map<String, PlacedOrder> getPlacedOrders() {
        return marketInfo.getPlacedOrders();
    }

    @GetMapping("/openedPositions/long")
    public List<OpenedPosition> getOpenedLongPositions() {
        return dailyVolumesStrategyCondition.getLongPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::symbol))
                .collect(Collectors.toList());
    }

    @GetMapping("/openedPositions/short")
    public List<OpenedPosition> getOpenedShortPositions() {
        return dailyVolumesStrategyCondition.getShortPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::symbol))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/openedPositions/long/{pair}")
    public void deletePairFromOpenedLongPositionsCache(@PathVariable String pair) {
        dailyVolumesStrategyCondition.getLongPositions().remove(pair.toUpperCase());
    }

    @GetMapping("/sellJournal")
    public List<SellRecord> getSellJournal() {
        return dailyVolumesStrategyCondition.getSellJournal().values().stream()
                .sorted(Comparator.comparing(SellRecord::sellTime).reversed())
                .collect(Collectors.toList());
    }

    @GetMapping("/candleStickEventsStreams")
    public List<String> getDailyCandleStickEventsStreams() {
        return daily.getCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    @GetMapping("/cachedCandleStickEvents")
    public Map<String, Deque<CandlestickEvent>> getDailyCachedCandleStickEvents() {
        return daily.getCachedCandlestickEvents();
    }

    @PostMapping("/userDataUpdateEvent")
    public void handleUserDataUpdateEvent(@RequestBody OrderTradeUpdateEvent event) {
        log.debug("Get order trade update event: {}", event);
        switch (event.getSide()) {
            case BUY -> daily.handleBuying(event);
            case SELL -> daily.handleSelling(event);
        }
    }


}
