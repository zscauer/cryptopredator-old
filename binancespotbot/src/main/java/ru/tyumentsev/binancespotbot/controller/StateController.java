package ru.tyumentsev.binancespotbot.controller;

import java.util.*;

import com.binance.api.client.domain.event.CandlestickEvent;
import lombok.extern.slf4j.Slf4j;
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
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.binancespotbot.service.SpotTrading;
import ru.tyumentsev.binancespotbot.strategy.BuyBigVolumeGrowth;

@RestController
@RequestMapping("/state")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class StateController {

    BinanceApiRestClient restClient;
    MarketData marketData;
    SpotTrading spotTrading;
    BuyBigVolumeGrowth buyBigVolumeGrowth;


    @GetMapping("/closeUserDataStream")
    public Map<String, String> closeUserDataStream() {
        try {
            buyBigVolumeGrowth.getAccountManager().closeCurrentUserDataStream();
            return Collections.singletonMap("response", "Command to close user data stream was successfully sent.");
        } catch (Exception e) {
            return Collections.singletonMap("response", e.getStackTrace().toString());
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

    @GetMapping("/buyBigVolumeChange/getCheapPairsWithoutOpenedPositions")
    public List<String> getCheapPairsWithoutOpenedPositions(@RequestParam String asset) {
        return marketData.getCheapPairsExcludeOpenedPositions(asset);
    }

    @GetMapping("/buyBigVolumeChange/getCachedCandleSticks")
    public Map<String, List<Candlestick>> getCachedCandleSticks() {
        return marketData.getCachedCandles();
    }

    @GetMapping("/buyBigVolumeChange/getCachedCandleStickEvents")
    public Map<String, Deque<CandlestickEvent>> getCachedCandleStickEvents() {
        return marketData.getCachedCandleStickEvents();
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

    @DeleteMapping("/openedPositions/long")
    public void closeAllOpenedLongPositions() {
        marketData.initializeOpenedLongPositionsFromMarket(buyBigVolumeGrowth.getMarketInfo(), buyBigVolumeGrowth.getAccountManager());
        Map<String, Double> positionsToClose = new HashMap<>();

        marketData.getLongPositions().forEach((pair, openedPosition) -> positionsToClose.put(pair,
                Double.parseDouble(restClient.getAccount().getAssetBalance(pair.replace("USDT", "")).getFree())));

        spotTrading.closePostitions(positionsToClose);
    }
}
