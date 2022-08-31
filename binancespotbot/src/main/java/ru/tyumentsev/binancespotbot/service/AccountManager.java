package ru.tyumentsev.binancespotbot.service;

import java.io.Closeable;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.UserDataUpdateEvent;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@Log4j2
public class AccountManager {

    @Autowired
    BinanceApiRestClient restClient;
    @Autowired
    BinanceApiWebSocketClient webSocketClient;

    String listenKey;

    public void initializeUserDataUpdateStream() {
        if (listenKey == null || listenKey.isEmpty()) { // get current user stream listen key.
            listenKey = restClient.startUserDataStream();
        }

        closeCurrentUserDataStream(); // needs to close previous stream if it's open.

        listenKey = restClient.startUserDataStream();
    }

    public void closeCurrentUserDataStream() {
        log.info("Sending request to close user data stream with listen key {}", listenKey);
        restClient.closeUserDataStream(listenKey);
    }

    /**
     * Keepalive a user data stream to prevent a time out.
     * User data streams will close after 60 minutes.
     * It's recommended to send a ping about every 30 minutes.
     */
    public void keepAliveUserDataUpdateStream() {
        restClient.keepAliveUserDataStream(listenKey);
    }

    public Double getFreeAssetBalance(String asset) {
        return Double.parseDouble(restClient.getAccount().getAssetBalance(asset).getFree());
    }

    public List<AssetBalance> getAccountBalances() {
        return restClient.getAccount().getBalances().stream()
                .filter(balance -> Double.parseDouble(balance.getFree()) > 0).toList();
    }

    public Closeable listenUserDataUpdateEvents(BinanceApiCallback<UserDataUpdateEvent> callback) {
        return webSocketClient.onUserDataUpdateEvent(listenKey, callback);
    }
}
