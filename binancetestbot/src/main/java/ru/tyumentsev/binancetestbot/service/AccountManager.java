package ru.tyumentsev.binancetestbot.service;

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
import lombok.Getter;
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

    @Getter
    String listenKey;

    public void fillListenKey() {
        listenKey = restClient.startUserDataStream();
    }

    public Double getFreeAssetBalance(String asset) {
        return Double.parseDouble(restClient.getAccount().getAssetBalance(asset).getFree());
    }

    public List<AssetBalance> getAccountBalances() {
        return restClient.getAccount().getBalances().stream().filter(balance -> Double.parseDouble(balance.getFree()) > 0).toList();
    }

    public Closeable listenUserDataUpdateEvents(BinanceApiCallback<UserDataUpdateEvent> callback) {
        return webSocketClient.onUserDataUpdateEvent(listenKey, callback);
    }

    public void keepAliveUserDataUpdateStream() {
        log.info("Sending listen key ({}) to keep alive user data stream.", listenKey);
        restClient.keepAliveUserDataStream(listenKey);
    }

}
