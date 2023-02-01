package ru.tyumentsev.cryptopredator.indicatorvirginbot;

import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNum;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.mapping.CandlestickToBaseBarMapper;
import ru.tyumentsev.cryptopredator.commons.service.AccountManager;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
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
    Closeable userDataUpdateEventsListener;
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.maximalPairPrice}")
    float maximalPairPrice;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        Map<String, TradingStrategy> activeStrategies = tradingStrategies.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        activeStrategies.forEach((name, implementation) -> implementation.prepareData());

        log.info("Application initialization complete. Active strategies: {}.", activeStrategies.keySet());

        if (testLaunch) {
            log.warn("Application launched in test mode. Deals functionality disabled.");
            runTest();
        }
    }

    private void runTest() {
        CandlestickInterval interval = CandlestickInterval.FIVE_MINUTES;
        List<Candlestick> candlestickList = marketInfo.getCandleSticks("TUSDT", interval, 20);
        log.info("Candlesticks: {}", candlestickList);
        List<Bar> barsList = CandlestickToBaseBarMapper.map(candlestickList, interval);
        log.info("Bars list: {}", barsList);
        BaseBarSeriesBuilder barSeriesBuilder = new BaseBarSeriesBuilder();
        barSeriesBuilder.withBars(barsList);
        barSeriesBuilder.withMaxBarCount(20);
        barSeriesBuilder.withNumTypeOf(DoubleNum::valueOf);
        BaseBarSeries series = barSeriesBuilder.build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice);

        log.info(macd.getLongTermEma().toString());
        log.info(macd.getShortTermEma().toString());
        log.info(macd.getValue(5).toString());

    }

    @Scheduled(fixedDelayString = "${strategy.global.initializeUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.initializeUserDataUpdateStream.initialDelay}")
    public void generalMonitoring_initializeAliveUserDataUpdateStream() {
        if (!testLaunch && tradingStrategies.values().stream().anyMatch(TradingStrategy::isEnabled)) {
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
