package ru.tyumentsev.cryptopredator.commons.service;

import java.io.Closeable;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.UserDataUpdateEvent;

import com.binance.api.client.exception.BinanceApiException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
@Slf4j
public class AccountManager implements TradingService {

    final BinanceApiRestClient restClient;
    final BinanceApiWebSocketClient webSocketClient;

    volatile List<AssetBalance> accountBalances;
    volatile Closeable userDataUpdateEventsListener;

    @Setter
    @Getter
    volatile String listenKey;

    public List<AssetBalance> getAccountBalances() {
        if (accountBalances == null) {
            refreshAccountBalances();
        }
        return accountBalances.stream()
                .sorted(Comparator.comparing(AssetBalance::getAsset))
                .toList();
    }

    public Float getFreeAssetBalance(final String asset) {
        return Float.parseFloat(accountBalances.stream()
                .filter(assetBalance -> assetBalance.getAsset().equalsIgnoreCase(asset))
                .findFirst().map(AssetBalance::getFree).orElse("0"));
    }

    protected AccountManager refreshAccountBalances() {
        accountBalances = restClient.getAccount().getBalances().stream()
                .filter(balance -> Float.parseFloat(balance.getFree()) > 0 || Float.parseFloat(balance.getLocked()) > 0)
                .toList();
        return this;
    }

    protected void initializeUserDataUpdateStream(BinanceApiCallback<UserDataUpdateEvent> callback) {
        if (listenKey == null || listenKey.isBlank()) { // get current user stream listen key.
            listenKey = restClient.startUserDataStream();
        }

        closeCurrentUserDataStream(); // needs to close previous stream if it's open.

        listenKey = restClient.startUserDataStream();

        if (userDataUpdateEventsListener != null) {
            try {
                userDataUpdateEventsListener.close();
            } catch (IOException e) {
                log.error("Error while trying to close user data update events listener:\n{}.", e.getMessage());
                e.printStackTrace();
            }
        }
        userDataUpdateEventsListener = webSocketClient.onUserDataUpdateEvent(listenKey, callback);
    }

    /**
     * Keepalive a user data stream to prevent a timeout.
     * User data streams will close after 60 minutes by Binance.
     * It's recommended to send a ping about every 30 minutes.
     */
    protected void keepAliveUserDataUpdateStream() {
        log.debug("Sending signal to keep alive user data update stream.");
        try {
            restClient.keepAliveUserDataStream(listenKey);
        } catch (BinanceApiException binanceApiException) {
            log.error("Catch BinanceApiException with error {}", binanceApiException.getError());
            if (binanceApiException.getMessage().toLowerCase().contains("listenKey does not exist".toLowerCase())) {
                closeCurrentUserDataStream();
                listenKey = restClient.startUserDataStream();
                log.info("Get new listenKey ({}).", listenKey);
            }
        }
    }

    public void closeCurrentUserDataStream() {
        log.debug("Sending request to close user data stream with listen key {}.", listenKey);
        restClient.closeUserDataStream(listenKey);
    }

}