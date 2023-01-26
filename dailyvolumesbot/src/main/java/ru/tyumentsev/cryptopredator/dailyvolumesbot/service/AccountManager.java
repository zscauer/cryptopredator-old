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

    @Getter
    Closeable userDataUpdateEventsListener;
    List<AssetBalance> currentBalances;

    String listenKey;

    @Scheduled(fixedDelayString = "${strategy.global.initializeUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.initializeUserDataUpdateStream.initialDelay}")
    public void generalMonitoring_initializeAliveUserDataUpdateStream() {
        if (!testLaunch) {
            // User data stream are closing by binance after 24 hours of opening.
            initializeUserDataUpdateStream();

            if (userDataUpdateEventsListener != null) {
                try {
                    userDataUpdateEventsListener.close();
                } catch (IOException e) {
                    log.error("Error while trying to close user data update events listener:\n{}.", e.getMessage());
                    e.printStackTrace();
                }
            }
            monitorUserDataUpdateEvents();
        }
    }

    @Scheduled(fixedDelayString = "${strategy.global.keepAliveUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.keepAliveUserDataUpdateStream.initialDelay}")
    public void generalMonitoring_keepAliveUserDataUpdateStream() {
        if (!testLaunch) {
            keepAliveUserDataUpdateStream();
        }
    }

    /**
     * Opens web socket stream of user data update events and monitors trade events.
     * If it was "buy" event, then add pair from this event to monitoring,
     * if it was "sell" event - removes from monitoring.
     */
    public void monitorUserDataUpdateEvents() {
        userDataUpdateEventsListener = listenUserDataUpdateEvents(callback -> {
            if (callback.getEventType() == UserDataUpdateEvent.UserDataUpdateEventType.ORDER_TRADE_UPDATE
                    && callback.getOrderTradeUpdateEvent().getExecutionType() == ExecutionType.TRADE) {
                OrderTradeUpdateEvent event = callback.getOrderTradeUpdateEvent();

                switch (event.getSide()) {
                    case BUY -> {
                        tradingStrategies.values().forEach(strategy -> strategy.handleBuying(event));
                    }
                    case SELL -> {
                        tradingStrategies.values().forEach(strategy -> strategy.handleSelling(event));
                    }
                }

                refreshAccountBalances();
            }
        });
    }

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
