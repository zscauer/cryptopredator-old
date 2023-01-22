package ru.tyumentsev.binancetradebot.binancespotbot.service;

import java.io.Closeable;
import java.util.List;

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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AccountManager {

    BinanceApiRestClient restClient;
    BinanceApiWebSocketClient webSocketClient;

    @NonFinal
    List<AssetBalance> currentBalances;

    @NonFinal
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
