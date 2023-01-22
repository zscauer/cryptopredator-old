package ru.tyumentsev.binancetradebot.binancespotbot.controller;

import java.time.LocalDateTime;
import java.util.*;

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
import ru.tyumentsev.binancetradebot.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancetradebot.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.binancetradebot.binancespotbot.service.SpotTrading;
import ru.tyumentsev.binancetradebot.binancespotbot.strategy.Daily;
import ru.tyumentsev.binancetradebot.binancespotbot.strategy.VolumeCatcher;

@RestController
@RequestMapping("/state")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class StateController {

    BinanceApiRestClient restClient;
    MarketData marketData;
    SpotTrading spotTrading;
    VolumeCatcher volumeCatcher;
    Daily daily;

    @NonFinal
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;


    @GetMapping("/closeUserDataStream")
    public Map<String, String> closeUserDataStream() {
        try {
            volumeCatcher.getAccountManager().closeCurrentUserDataStream();
            return Collections.singletonMap("response", "Command to close user data stream was successfully sent.");
        } catch (Exception e) {
            return Collections.singletonMap("response", Arrays.toString(e.getStackTrace()));
        }
    }

    @GetMapping("/accountBalance")
    public List<AssetBalance> accountBalance() {
        Account account = restClient.getAccount();
        return account.getBalances().stream()
                .filter(balance -> Double.parseDouble(balance.getFree()) > 0
                        || Double.parseDouble(balance.getLocked()) > 0)
                .toList();
    }

    @GetMapping("/accountBalance/{ticker}")
    public AssetBalance assetBalance(@PathVariable String ticker) {
        Account account = restClient.getAccount();
        return account.getAssetBalance(ticker.toUpperCase());
    }

    @GetMapping("/openedPositions/long")
    public Map<String, OpenedPosition> getOpenedLongPositions() {
        return marketData.getLongPositions();
    }

    @GetMapping("/openedPositions/short")
    public Map<String, OpenedPosition> getOpenedShortPositions() {
        return marketData.getShortPositions();
    }

    @DeleteMapping("/openedPositions/long/{pair}")
    public void deletePairFromOpenedLongPositionsCache(@PathVariable String pair) {
        marketData.getLongPositions().remove(pair.toUpperCase());
    }

    @GetMapping("/volumeCatcher/getCheapPairsWithoutOpenedPositions")
    public List<String> getCheapPairsWithoutOpenedPositions(@RequestParam String asset) {
        return marketData.getCheapPairsExcludeOpenedPositions(asset);
    }

    @GetMapping("/volumeCatcher/getCachedCandleSticks")
    public Map<String, List<Candlestick>> getCachedCandleSticks() {
        return marketData.getCachedCandles();
    }

    @GetMapping("/volumeCatcher/getCachedCandleStickEvents")
    public Map<String, Deque<CandlestickEvent>> getCachedCandleStickEvents() {
        return volumeCatcher.getCachedCandlestickEvents();
    }

    @GetMapping("/volumeCatcher/candleStickEventsStreams")
    public Set<String> getVolumeCatcherCandleStickEventsStreams() {
        return volumeCatcher.getCandleStickEventsStreams().keySet();
    }

    @GetMapping("/sellJournal")
    public Map<String, LocalDateTime> getSellJournal() {
        return marketData.getSellJournal();
    }

    @DeleteMapping("/openedPositions/long")
    public void closeAllOpenedLongPositions() {
        marketData.initializeOpenedLongPositionsFromMarket(volumeCatcher.getMarketInfo(), volumeCatcher.getAccountManager());
        Map<String, Double> positionsToClose = new HashMap<>();

        marketData.getLongPositions().forEach((pair, openedPosition) -> positionsToClose.put(pair,
                Double.parseDouble(restClient.getAccount().getAssetBalance(pair.replace(tradingAsset, "")).getFree())));

        spotTrading.closePostitions(positionsToClose);
    }

    @GetMapping("/daily/candleStickEventsStreams")
    public Set<String> getDailyCandleStickEventsStreams() {
        return daily.getCandleStickEventsStreams().keySet();
    }

    @GetMapping("/daily/getCachedCandleStickEvents")
    public Map<String, Deque<CandlestickEvent>> getDailyCachedCandleStickEvents() {
        return daily.getCachedCandlestickEvents();
    }
}
