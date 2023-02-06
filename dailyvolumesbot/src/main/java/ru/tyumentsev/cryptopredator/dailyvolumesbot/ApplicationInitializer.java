package ru.tyumentsev.cryptopredator.dailyvolumesbot;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import lombok.Getter;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.cache.StrategyCondition;
import ru.tyumentsev.cryptopredator.commons.cache.StrategyCondition;
import ru.tyumentsev.cryptopredator.commons.service.AccountManager;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class ApplicationInitializer implements ApplicationRunner {

    final MarketInfo marketInfo;
    final AccountManager accountManager;
    final Map<String, TradingStrategy> tradingStrategies;

    @Getter
    volatile Closeable userDataUpdateEventsListener;
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.maximalPairPrice}")
    float maximalPairPrice;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (testLaunch) {
            log.warn("Application launched in test mode. Deals functionality disabled.");
        }

        marketInfo.getAvailableTradePairs(tradingAsset);
        marketInfo.fillCheapPairs(tradingAsset, maximalPairPrice);

        Map<String, TradingStrategy> activeStrategies = tradingStrategies.values().stream()
                .filter(TradingStrategy::isEnabled)
                .collect(Collectors.toMap(value -> String.format("%s (id: %s)", value.getName(), value.getId()), value -> value));

        activeStrategies.forEach((name, implementation) -> implementation.prepareData());

        log.info("Application initialization complete. Active strategies: {}.", activeStrategies.keySet());
    }

    @Scheduled(fixedDelayString = "${strategy.global.initializeUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.initializeUserDataUpdateStream.initialDelay}")
    public void generalMonitoring_initializeAliveUserDataUpdateStream() {
        if (//!testLaunch &&
                tradingStrategies.values().stream().anyMatch(TradingStrategy::isEnabled)) {
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
     * Opens web socket stream of user data update events and monitors trade events.
     * If it was "buy" event, then add pair from this event to monitoring,
     * if it was "sell" event - removes from monitoring.
     */
    public void monitorUserDataUpdateEvents() {
        userDataUpdateEventsListener = accountManager.listenUserDataUpdateEvents(new BinanceApiCallback<UserDataUpdateEvent>() {
            @Override
            public void onResponse(UserDataUpdateEvent callback) {
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
            }

            @Override
            public void onFailure(Throwable cause) {
                BinanceApiCallback.super.onFailure(cause);
                log.error("Binance API callback failure: {}", cause.getMessage());
            }
        });

    }

    @PreDestroy
    public void destroy() {
        accountManager.closeCurrentUserDataStream();
    }
}
