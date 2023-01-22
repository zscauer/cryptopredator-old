package ru.tyumentsev.cryptopredator.binancespotbot.service;

import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.binancespotbot.cache.MarketData;
import ru.tyumentsev.cryptopredator.binancespotbot.strategy.TradingStrategy;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class GeneralMonitoring {

    // TODO: move this functionality to separate application
    final AccountManager accountManager;
    final MarketData marketData;
    final MarketInfo marketInfo;
    final SpotTrading spotTrading;

    final Map<String, TradingStrategy> tradingStrategies;

    @Getter
    Closeable userDataUpdateEventsListener;

    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;

    private static Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    @Scheduled(fixedDelayString = "${strategy.global.initializeUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.initializeUserDataUpdateStream.initialDelay}")
    public void generalMonitoring_initializeAliveUserDataUpdateStream() {
        if (!testLaunch) {
            // User data stream are closing by binance after 24 hours of opening.
            accountManager.initializeUserDataUpdateStream();

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
            accountManager.keepAliveUserDataUpdateStream();
        }
    }

    /**
     * Search for not executed orders to cancel them.
     */
    @Scheduled(fixedDelayString = "${strategy.monitoring.cancelExpiredOrders.fixedDelay}", initialDelayString = "${strategy.monitoring.cancelExpiredOrders.initialDelay}")
    public void generalMonitoring_cancelExpiredOrders() {
        //TODO: find way to get info about active orders
    }

    /**
     * Opens web socket stream of user data update events and monitors trade events.
     * If it was "buy" event, then add pair from this event to monitoring,
     * if it was "sell" event - removes from monitoring.
     */
    public void monitorUserDataUpdateEvents() {
        userDataUpdateEventsListener = accountManager.listenUserDataUpdateEvents(callback -> {
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

                accountManager.refreshAccountBalances();
            }
        });
    }
}
