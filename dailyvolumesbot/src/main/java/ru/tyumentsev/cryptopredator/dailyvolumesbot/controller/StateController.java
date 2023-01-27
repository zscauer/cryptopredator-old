package ru.tyumentsev.cryptopredator.dailyvolumesbot.controller;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.binance.api.client.domain.event.CandlestickEvent;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.market.Candlestick;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.cache.MarketData;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.domain.SellRecord;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.service.MarketInfo;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.service.SpotTrading;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.strategy.Daily;

@RestController
@RequestMapping("/state")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class StateController {

    BinanceApiRestClient restClient;
    MarketData marketData;
    MarketInfo marketInfo;
    Daily daily;

    @GetMapping("/accountBalance")
    public List<AssetBalance> accountBalance() {
        Account account = restClient.getAccount();
        return account.getBalances().stream()
                .filter(balance -> Double.parseDouble(balance.getFree()) > 0
                        || Double.parseDouble(balance.getLocked()) > 0)
                .sorted(Comparator.comparing(AssetBalance::getAsset))
                .toList();
    }

    @GetMapping("/accountBalance/{ticker}")
    public AssetBalance assetBalance(@PathVariable String ticker) {
        Account account = restClient.getAccount();
        return account.getAssetBalance(ticker.toUpperCase());
    }

    @GetMapping("/processedOrders")
    public Map<String, Boolean> getProcessedOrders() {
        return marketInfo.getProcessedOrders();
    }

    @GetMapping("/openedPositions/long")
    public List<OpenedPosition> getOpenedLongPositions() {
        return marketData.getLongPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::symbol))
                .collect(Collectors.toList());
    }

    @GetMapping("/openedPositions/short")
    public List<OpenedPosition> getOpenedShortPositions() {
        return marketData.getShortPositions().values().stream()
                .sorted(Comparator.comparing(OpenedPosition::symbol))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/openedPositions/long/{pair}")
    public void deletePairFromOpenedLongPositionsCache(@PathVariable String pair) {
        marketData.getLongPositions().remove(pair.toUpperCase());
    }

    @GetMapping("/sellJournal")
    public List<SellRecord> getSellJournal() {
        return marketData.getSellJournal().values().stream()
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
        return marketData.getCachedCandlestickEvents();
    }
}
