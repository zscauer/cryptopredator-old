package ru.tyumentsev.binancespotbot.controller;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import lombok.experimental.NonFinal;
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

    @NonFinal
    Closeable openedWebSocket;

    @GetMapping("/closeWS")
    public Map<String, String> closeWebSocket() {
        try {
            if (openedWebSocket == null) {
                return Collections.singletonMap("response", "WebSocket is null");
            } else {
                openedWebSocket.close();
                return Collections.singletonMap("response", "Closing web socket " + openedWebSocket.toString());
            }
        } catch (IOException e) {
            return Collections.singletonMap("response", e.getStackTrace().toString());
        }
    }

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

    @GetMapping("/openedPositions")
    public Map<String, OpenedPosition> getOpenedPositions() {
        return marketData.getOpenedPositions();
    }

    @DeleteMapping("/openedPositions/{pair}")
    public void deletePairFromOpenedPositionsCache(@PathVariable String pair) {
        marketData.getOpenedPositions().remove(pair.toUpperCase());
    }

    @DeleteMapping("/openedPositions")
    public void closeAllOpenedPositions() {
        marketData.initializeOpenedPositionsFromMarket(buyBigVolumeGrowth.getMarketInfo(), buyBigVolumeGrowth.getAccountManager());
        Map<String, Double> positionsToClose = new HashMap<>();

        marketData.getOpenedPositions().forEach((key, value) -> positionsToClose.put(key,
                Double.parseDouble(restClient.getAccount().getAssetBalance(key.replace("USDT", "")).getFree())));

        spotTrading.closePostitions(positionsToClose);
    }
}
