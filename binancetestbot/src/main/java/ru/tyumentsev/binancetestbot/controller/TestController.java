package ru.tyumentsev.binancetestbot.controller;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.CandlestickEvent;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.tyumentsev.binancetestbot.cache.MarketData;

@RestController
@RequestMapping("/state")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class TestController {

    final BinanceApiRestClient restClient;
    final MarketData marketData;

    Closeable openedWebSocket;

    @GetMapping(value = "/closeWS")
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

    @GetMapping("/openedPositions")
    public Map<String, Double> getOpenedPositions() {
        return marketData.getOpenedPositionsCache();
    }

    @GetMapping("/buyBigVolumeChange/getMonitoredCandleSticks")
    public Map<String, CandlestickEvent> getMonitoredCandleSticks(@RequestParam("asset") String asset) {
        return marketData.getCachedCandleStickEvents();
    }

    @DeleteMapping("/openedPositions")
    public void deletePairFromOpenedPositionsCache(@RequestBody String pair) {
        marketData.getOpenedPositionsCache().remove(pair.toUpperCase());
    }

}
