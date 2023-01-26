package ru.tyumentsev.cryptopredator.dailyvolumesbot.service;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.UserDataUpdateEvent;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.strategy.TradingStrategy;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class AccountManager implements TradingService {

    final BinanceApiRestClient restClient;
    final BinanceApiWebSocketClient webSocketClient;
    final Map<String, TradingStrategy> tradingStrategies;

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;

    List<AssetBalance> currentBalances;

    String listenKey;

    public void initializeUserDataUpdateStream() {
        if (listenKey == null || listenKey.isEmpty()) { // get current user stream listen key.
            listenKey = restClient.startUserDataStream();
        }

        closeCurrentUserDataStream(); // needs to close previous stream if it's open.

        listenKey = restClient.startUserDataStream();
    }

    public void closeCurrentUserDataStream() {
        log.debug("Sending request to close user data stream with listen key {}.", listenKey);
        restClient.closeUserDataStream(listenKey);
    }

    /**
     * Keepalive a user data stream to prevent a timeout.
     * User data streams will close after 60 minutes by Binance.
     * It's recommended to send a ping about every 30 minutes.
     */
    public void keepAliveUserDataUpdateStream() {
        log.debug("Sending signal to keep alive user data update stream.");
        restClient.keepAliveUserDataStream(listenKey);
    }

    public Double getFreeAssetBalance(String asset) {
        return Double.parseDouble(restClient.getAccount().getAssetBalance(asset).getFree());
    }

    public AccountManager refreshAccountBalances() {
        currentBalances = restClient.getAccount().getBalances().stream()
                .filter(balance -> Double.parseDouble(balance.getFree()) > 0).toList();
        return this;
    }

    public List<AssetBalance> getAccountBalances() {
        return currentBalances;
    }

    public Closeable listenUserDataUpdateEvents(BinanceApiCallback<UserDataUpdateEvent> callback) {
        return webSocketClient.onUserDataUpdateEvent(listenKey, callback);
    }
}
