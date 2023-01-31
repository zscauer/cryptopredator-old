package ru.tyumentsev.cryptopredator.indicatorvirginbot.strategy;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.market.CandlestickInterval;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;
import ru.tyumentsev.cryptopredator.indicatorvirginbot.cache.IndicatorVirginStrategyCondition;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class IndicatorVirgin implements TradingStrategy {

    final MarketInfo marketInfo;
    IndicatorVirginStrategyCondition indicatorVirginStrategyCondition;
    final SpotTrading spotTrading;
    final DataService dataService;

    CandlestickInterval candlestickInterval;
    @Getter
    final Map<String, Closeable> candleStickEventsStreams = new ConcurrentHashMap<>();

    final static String STRATEGY_NAME = "indicatorvirgin";

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.indicatorvirgin.enabled}")
    boolean indicatorVirginEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;


    @Scheduled(fixedDelayString = "${strategy.indicatorvirgin.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.indicatorvirgin.startCandlstickEventsCacheUpdating.initialDelay}")
    public void daily_startCandlstickEventsCacheUpdating() {
        if (indicatorVirginEnabled && !testLaunch) {
            startCandlstickEventsCacheUpdating(CandlestickInterval.DAILY);
//            Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).forEach(thread -> {
//                log.info("Thread {} name: {} group: {}.", thread.getId(), thread.getName(), thread.getThreadGroup());
//            });
        }
    }

    @Override
    public boolean isEnabled() {
        return indicatorVirginEnabled;
    }

    @Override
    public void prepareData() {

    }

    @Override
    public void handleBuying(OrderTradeUpdateEvent orderTradeUpdateEvent) {

    }

    @Override
    public void handleSelling(OrderTradeUpdateEvent orderTradeUpdateEvent) {

    }

    public void startCandlstickEventsCacheUpdating(CandlestickInterval interval) {
        candlestickInterval = interval;
        closeOpenedWebSocketStreams();
        AtomicInteger marketMonitoringThreadsCounter = new AtomicInteger();
        AtomicInteger longMonitoringThreadsCounter = new AtomicInteger();

        marketInfo.getCheapPairsExcludeOpenedPositions(tradingAsset, indicatorVirginStrategyCondition.getLongPositions().keySet(), indicatorVirginStrategyCondition.getShortPositions().keySet()).forEach(ticker -> {
            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    marketMonitoringCallback(ticker)));
            marketMonitoringThreadsCounter.getAndIncrement();
        });
        indicatorVirginStrategyCondition.getLongPositions().forEach((ticker, openedPosition) -> {
            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    longPositionMonitoringCallback(ticker)));
            longMonitoringThreadsCounter.getAndIncrement();
        });

        log.info("Runned {} market monitoring threads and {} long monitoring threads.", marketMonitoringThreadsCounter, longMonitoringThreadsCounter);
    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback(String ticker) {
        return event -> {
            addCandlestickEventToCache(ticker, event);

            var currentEvent = cachedCandlestickEvents.get(ticker).getLast();
            var previousEvent = cachedCandlestickEvents.get(ticker).getFirst();

            var closePrice = parsedFloat(event.getClose());
            var openPrice = parsedFloat(event.getOpen());

            if (closePrice > openPrice * priceGrowthFactor
                    && (parsedFloat(currentEvent.getVolume()) > parsedFloat(previousEvent.getVolume()) * volumeGrowthFactor
                    || percentageDifference(closePrice, openPrice) > rocketCandidatePercentageGrowth) // if price grown a lot without volume, it might be a rocket.
                    && percentageDifference(parsedFloat(event.getHigh()), closePrice) < 4) { // candle's max price not much higher than current.
                buyFast(ticker, closePrice, tradingAsset, false);
                rocketCandidates.put(ticker, percentageDifference(closePrice, openPrice) > rocketCandidatePercentageGrowth
                        && !(parsedFloat(currentEvent.getVolume()) > parsedFloat(previousEvent.getVolume()) * volumeGrowthFactor));
            }
        };
    }

    private BinanceApiCallback<CandlestickEvent> longPositionMonitoringCallback(String ticker) {
        return event -> {
            addCandlestickEventToCache(ticker, event);

            List<AssetBalance> currentBalances = spotTrading.getAccountBalances().stream()
                    .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();

            if (currentBalances.isEmpty()) {
                log.warn("No available trading assets found on binance account, but long position monitoring for '{}' is still executing.", ticker);
                return;
            }

            Optional.ofNullable(dailyVolumesStrategyCondition.getLongPositions().get(ticker)).ifPresent(openedPosition -> {
                var currentPrice = parsedFloat(event.getClose());

                dailyVolumesStrategyCondition.updateOpenedPositionLastPrice(ticker, currentPrice, dailyVolumesStrategyCondition.getLongPositions());

                if (currentPrice > openedPosition.avgPrice() * pairTakeProfitFactor) {
                    log.info("Current price decrease factor of {} is {}.", openedPosition.symbol(), dailyVolumesStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());
//                    marketData.updatePriceDecreaseFactor(ticker, takeProfitPriceDecreaseFactor, marketData.getLongPositions());
                    openedPosition.priceDecreaseFactor(takeProfitPriceDecreaseFactor);
                    log.info("Price decrease factor of {} after changing is {}.", openedPosition.symbol(), dailyVolumesStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());

                    if (averagingEnabled) {
                        buyFast(ticker, currentPrice, tradingAsset, true);
                    }
                }
                float stopTriggerValue = openedPosition.priceDecreaseFactor() == takeProfitPriceDecreaseFactor ? openedPosition.maxPrice() : openedPosition.avgPrice();

                if (currentPrice < stopTriggerValue * openedPosition.priceDecreaseFactor()) {
                    log.info("PRICE of {} DECREASED and now equals {}, price decrease factor is {} / {}.",
                            ticker, currentPrice, openedPosition.priceDecreaseFactor(), dailyVolumesStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());
                    sellFast(ticker, openedPosition.qty(), tradingAsset);
//                } else if (averagingEnabled && currentPrice > openedPosition.avgPrice() * averagingTriggerFactor) {
//                    log.info("PRICE of {} GROWTH more than AVG ({}) and now equals {}.", ticker, openedPosition.avgPrice(), currentPrice);
////                    openedPosition.priceDecreaseFactor(1D - (1D - pairTakeProfitFactor) / 2);
//                    buyFast(ticker, currentPrice, tradingAsset);
                }
            });
        };
    }

    public void addCandlestickEventToCache(String ticker, CandlestickEvent candlestickEvent) {
        Deque<CandlestickEvent> eventsQueue = Optional.ofNullable(cachedCandlestickEvents.get(ticker)).orElseGet(() -> {
            cachedCandlestickEvents.put(ticker, new LinkedList<>());
            return cachedCandlestickEvents.get(ticker);
        });
        Optional.ofNullable(eventsQueue.peekLast()).ifPresentOrElse(lastCachedEvent -> {
            if (lastCachedEvent.getOpenTime().equals(candlestickEvent.getOpenTime())) { // refreshed candle event.
                eventsQueue.remove(lastCachedEvent); // remove previous version of this event.
            }
            eventsQueue.addLast(candlestickEvent);
        }, () -> eventsQueue.addLast(candlestickEvent));

        if (eventsQueue.size() > candlestickEventsCacheSize) {
            eventsQueue.removeFirst();
        }
    }


    private void closeOpenedWebSocketStreams() {
        candleStickEventsStreams.forEach((pair, stream) -> {
            try {
                stream.close();
                log.debug("WebStream of '{}' closed.", pair);
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        });
        candleStickEventsStreams.clear();
    }

    @PreDestroy
    public void destroy() {
        closeOpenedWebSocketStreams();
        backupSellRecords();
        backupOpenedPositions();
        backupPreviousCandleData();
    }
}
