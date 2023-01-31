package ru.tyumentsev.cryptopredator.dailyvolumesbot.controller;

import java.util.*;
import java.util.stream.Collectors;

import com.binance.api.client.domain.event.CandlestickEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;

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
public class StateController {

    BinanceApiRestClient restClient;
    DailyVolumesStrategyCondition dailyVolumesStrategyCondition;
    MarketInfo marketInfo;
    Daily daily;

    @GetMapping("/accountBalance")
    public List<AssetBalance> accountBalance() {
        Account account = restClient.getAccount();
        return account.getBalances().stream()
                .filter(balance -> Float.parseFloat(balance.getFree()) > 0
                        || Float.parseFloat(balance.getLocked()) > 0)
                .sorted(Comparator.comparing(AssetBalance::getAsset))
                .toList();
    }

    @GetMapping("/accountBalance/{ticker}")
    public AssetBalance assetBalance(@PathVariable String ticker) {
        Account account = restClient.getAccount();
        return account.getAssetBalance(ticker.toUpperCase());
    }

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
}
