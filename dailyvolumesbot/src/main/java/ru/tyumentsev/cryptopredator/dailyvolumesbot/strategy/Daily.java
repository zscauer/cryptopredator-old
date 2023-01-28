package ru.tyumentsev.cryptopredator.dailyvolumesbot.strategy;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.market.CandlestickInterval;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.cache.MarketData;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.commons.mapping.CandlestickToEventMapper;
//import ru.tyumentsev.cryptopredator.dailyvolumesbot.service.CacheService;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.service.SpotTrading;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class Daily implements TradingStrategy {

    final MarketInfo marketInfo;
    final MarketData marketData;
    final SpotTrading spotTrading;
//    final CacheService cacheService;
    final DataService dataService;

    CandlestickInterval candlestickInterval;
    @Getter
    final Map<String, Closeable> candleStickEventsStreams = new ConcurrentHashMap<>();

    final Map<String, Boolean> rocketCandidates;
    final LocalTime eveningStopTime = LocalTime.of(22, 0), nightStopTime = LocalTime.of(3, 5);

    @NonFinal
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.daily.enabled}")
    boolean dailyEnabled;
    @Value("${strategy.daily.averagingEnabled}")
    boolean averagingEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.daily.volumeGrowthFactor}")
    float volumeGrowthFactor;
    @Value("${strategy.daily.priceGrowthFactor}")
    float priceGrowthFactor;
    @Value("${strategy.daily.pairTakeProfitFactor}")
    float pairTakeProfitFactor;
    @Value("${strategy.daily.priceDecreaseFactor}")
    float priceDecreaseFactor;
    @Value("${strategy.daily.takeProfitPriceDecreaseFactor}")
    float takeProfitPriceDecreaseFactor;
    @Value("${strategy.daily.averagingTriggerFactor}")
    float averagingTriggerFactor;
    @Value("${strategy.daily.rocketCandidatePercentageGrowth}")
    float rocketCandidatePercentageGrowth;

    @Scheduled(fixedDelayString = "${strategy.daily.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.daily.startCandlstickEventsCacheUpdating.initialDelay}")
    public void daily_startCandlstickEventsCacheUpdating() {
        if (dailyEnabled && !testLaunch) {
            startCandlstickEventsCacheUpdating(tradingAsset, CandlestickInterval.DAILY);
//            Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).forEach(thread -> {
//                log.info("Thread {} name: {} group: {}.", thread.getId(), thread.getName(), thread.getThreadGroup());
//            });
        }
    }

    @Override
    public boolean isEnabled() {
        return dailyEnabled;
    }

    @Override
    public void prepareData() {
        marketData.constructCandleStickEventsCache(tradingAsset);

        restoreCachedCandlestickEvents("DAILY");
        var cachedCandlestickEvents = marketData.getCachedCandlestickEvents();

        cachedCandlestickEvents.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(entry -> {
                    var candlestick = marketInfo.getCandleSticks(entry.getKey(), CandlestickInterval.DAILY, 2).get(0);
                    return CandlestickToEventMapper.map(entry.getKey(), candlestick);
                }).filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(event -> marketData.addCandlestickEventToCache(event.getSymbol(), event)
                );

        log.info("Prepared candlestick events of {} pairs.", cachedCandlestickEvents.values().stream().filter(value -> !value.isEmpty()).count());

        restoreSellJournalFromCache();
        prepareOpenedLongPositions();
    }

    private void restoreSellJournalFromCache() {
        var sellJournal = marketData.getSellJournal();
        dataService.findAllSellRecords().forEach(record -> sellJournal.put(record.symbol(), record));
        dataService.deleteAllSellRecords();
    }

    private void prepareOpenedLongPositions() {
        Map<String, OpenedPosition> cachedPositions = new HashMap<>();
        dataService.findAllOpenedPositions().forEach(cachedPosition -> cachedPositions.put(cachedPosition.symbol(), cachedPosition));

        marketData.getLongPositions().values().forEach(openedPosition -> {
            Optional.ofNullable(cachedPositions.get(openedPosition.symbol()))
                    .ifPresentOrElse(cachedPosition -> {
                        openedPosition.avgPrice(cachedPosition.avgPrice());
                        openedPosition.priceDecreaseFactor(cachedPosition.priceDecreaseFactor());
                        openedPosition.rocketCandidate(cachedPosition.rocketCandidate());
                        if (cachedPosition.maxPrice() > openedPosition.maxPrice()) {
                            openedPosition.maxPrice(cachedPosition.maxPrice());
                        }
                    }, () -> openedPosition.priceDecreaseFactor(priceDecreaseFactor));
        });

        dataService.deleteAllOpenedPositions();
    }

    private void restoreCachedCandlestickEvents(String interval) {
        int yesterdayDayOfYear = LocalDateTime.now().minusDays(1L).getDayOfYear();
        var cachedCandlestickEvents = marketData.getCachedCandlestickEvents();

        List<PreviousCandleData> dailyCachedCandleData = dataService.findAllPreviousCandleData().stream().filter(data -> data.id().startsWith(interval)).toList();
        log.debug("Size of empty cached candlestick events before restoring from cache is: {}.",
                cachedCandlestickEvents.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).collect(Collectors.toSet()).size());

        dailyCachedCandleData.stream()
                .filter(data -> LocalDateTime.ofInstant(Instant.ofEpochMilli(data.event().getOpenTime()), ZoneId.systemDefault()).getDayOfYear() == yesterdayDayOfYear)
                .forEach(element -> marketData.addCandlestickEventToCache(element.event().getSymbol(), element.event()));

        var emptyCachedCandlestickEvents = cachedCandlestickEvents.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).collect(Collectors.toSet());
        log.debug("Size of empty cached candlestick events after restoring from cache is: {} ({}).",
                emptyCachedCandlestickEvents.size(), emptyCachedCandlestickEvents);

        dataService.deleteAllPreviousCandleData(dailyCachedCandleData);
    }

    @Override
    public void handleBuying(final OrderTradeUpdateEvent buyEvent) {
        if (dailyEnabled
                && parsedFloat(buyEvent.getAccumulatedQuantity()) == parsedFloat(buyEvent.getOriginalQuantity())) {
            final String symbol = buyEvent.getSymbol();

            Optional.ofNullable(candleStickEventsStreams.remove(symbol)).ifPresent(candlestickEventsStream -> {
                try {
                    candlestickEventsStream.close();
                    log.debug("Candlestick events stream of {} closed in handle buying.", symbol);
                } catch (IOException e) {
                    log.error("Error while trying to close candlestick events stream of '{}':\n{}", symbol, e.getMessage());
                }
            });

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(buyEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(symbol).getPrice())
                    : parsedFloat(buyEvent.getPrice());

            log.info("BUY {} {} at {}.",
                    buyEvent.getAccumulatedQuantity(), symbol, dealPrice);
            marketData.putLongPositionToPriceMonitoring(symbol, dealPrice, parsedFloat(buyEvent.getAccumulatedQuantity()),
                    priceDecreaseFactor, Optional.ofNullable(rocketCandidates.remove(symbol)).orElse(false)
            );

            candleStickEventsStreams.put(symbol, marketInfo.openCandleStickEventsStream(symbol.toLowerCase(), candlestickInterval,
                    longPositionMonitoringCallback(symbol)));

            marketInfo.pairOrderFilled(symbol);
        }
    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent sellEvent) {
        if (dailyEnabled
                && parsedFloat(sellEvent.getAccumulatedQuantity()) == (parsedFloat(sellEvent.getOriginalQuantity()))) {
            Optional.ofNullable(candleStickEventsStreams.remove(sellEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
                try {
                    candlestickEventsStream.close();
                    log.debug("Candlestick events stream of {} closed in handle selling.", sellEvent.getSymbol());
                } catch (IOException e) {
                    log.error("Error while trying to close candlestick event stream of '{}':\n{}", sellEvent.getSymbol(), e.getMessage());
                }
            });

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(sellEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(sellEvent.getSymbol()).getPrice())
                    : parsedFloat(sellEvent.getPrice());

            log.info("SELL {} {} at {}.",
                    sellEvent.getOriginalQuantity(), sellEvent.getSymbol(), dealPrice);

            marketData.removeLongPositionFromPriceMonitoring(sellEvent.getSymbol());
            marketData.addSellRecordToJournal(sellEvent.getSymbol());

            candleStickEventsStreams.put(sellEvent.getSymbol(), marketInfo.openCandleStickEventsStream(sellEvent.getSymbol().toLowerCase(), candlestickInterval,
                    marketMonitoringCallback(sellEvent.getSymbol())));

            marketInfo.pairOrderFilled(sellEvent.getSymbol());
        }
    }

    public void startCandlstickEventsCacheUpdating(String asset, CandlestickInterval interval) {
        candlestickInterval = interval;
        closeOpenedWebSocketStreams();
        AtomicInteger marketMonitoringThreadsCounter = new AtomicInteger();
        AtomicInteger longMonitoringThreadsCounter = new AtomicInteger();

        marketData.getCheapPairsExcludeOpenedPositions(asset).forEach(ticker -> {
            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    marketMonitoringCallback(ticker)));
            marketMonitoringThreadsCounter.getAndIncrement();
        });
        marketData.getLongPositions().forEach((ticker, openedPosition) -> {
            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    longPositionMonitoringCallback(ticker)));
            longMonitoringThreadsCounter.getAndIncrement();
        });

        log.info("Runned {} market monitoring threads and {} long monitoring threads.", marketMonitoringThreadsCounter, longMonitoringThreadsCounter);
    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback(String ticker) {
        return event -> {
            marketData.addCandlestickEventToCache(ticker, event);

            var currentEvent = marketData.getCachedCandlestickEvents().get(ticker).getLast();
            var previousEvent = marketData.getCachedCandlestickEvents().get(ticker).getFirst();

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
            marketData.addCandlestickEventToCache(ticker, event);

            List<AssetBalance> currentBalances = spotTrading.getAccountBalances().stream()
                    .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();

            if (currentBalances.isEmpty()) {
                log.warn("No available trading assets found on binance account, but long position monitoring for '{}' is still executing.", ticker);
                return;
            }

            Optional.ofNullable(marketData.getLongPositions().get(ticker)).ifPresent(openedPosition -> {
                var currentPrice = parsedFloat(event.getClose());

                marketData.updateOpenedPosition(ticker, currentPrice, marketData.getLongPositions());

                if (currentPrice > openedPosition.avgPrice() * pairTakeProfitFactor) {
                    log.debug("Current price decrease factor of {} is {}.", openedPosition.symbol(), openedPosition.priceDecreaseFactor());
//                    marketData.updatePriceDecreaseFactor(ticker, takeProfitPriceDecreaseFactor, marketData.getLongPositions());
                    openedPosition.priceDecreaseFactor(takeProfitPriceDecreaseFactor);
                    if (averagingEnabled) {
                        buyFast(ticker, currentPrice, tradingAsset, true);
                    }
                }
                float stopTriggerValue = openedPosition.priceDecreaseFactor() == takeProfitPriceDecreaseFactor ? openedPosition.maxPrice() : openedPosition.avgPrice();

                if (currentPrice < stopTriggerValue * openedPosition.priceDecreaseFactor()) {
                    log.debug("PRICE of {} DECREASED and now equals {}.", ticker, currentPrice);
                    sellFast(ticker, openedPosition.qty(), tradingAsset);
//                } else if (averagingEnabled && currentPrice > openedPosition.avgPrice() * averagingTriggerFactor) {
//                    log.info("PRICE of {} GROWTH more than AVG ({}) and now equals {}.", ticker, openedPosition.avgPrice(), currentPrice);
////                    openedPosition.priceDecreaseFactor(1D - (1D - pairTakeProfitFactor) / 2);
//                    buyFast(ticker, currentPrice, tradingAsset);
                }
            });
        };
    }

    private void buyFast(final String symbol, final float price, String quoteAsset, boolean itsAveraging) {
        if ((itsDealsAllowedPeriod(LocalTime.now()) || itsAveraging) &&
                !(marketInfo.pairOrderIsProcessing(symbol) || marketData.thisSignalWorkedOutBefore(symbol))) {
            log.debug("Price of {} growth more than {}%, and now equals {}.", symbol, Float.valueOf(100 * priceGrowthFactor - 100).intValue(), price);
            spotTrading.placeBuyOrderFast(symbol, price, quoteAsset);
        }
    }

    private void sellFast(String symbol, float qty, String quoteAsset) {
        if (!marketInfo.pairOrderIsProcessing(symbol)) {
            spotTrading.placeSellOrderFast(symbol, qty);
        }

//        if (matchTrend) {
//            List<Candlestick> candleSticks = marketInfo.getCandleSticks(symbol, CandlestickInterval.DAILY, 2);
//            if (marketInfo.pairHadTradesInThePast(candleSticks, 2)) {
//                // current price higher then close price of previous day more than rocketFactor
//                // - there is rocket.
//                if (parsedFloat(candleSticks.get(1).getClose()) > parsedFloat(candleSticks.get(0).getClose())
//                        * rocketFactor) {
//                    spotTrading.placeSellOrderFast(symbol, qty);
//                } else if (parsedFloat(candleSticks.get(0).getClose()) > parsedFloat(candleSticks.get(1).getClose())
//                        * priceGrowthFactor) { // close price of previous day is higher than current more than growth
//                    // factor - there is downtrend.
//                    spotTrading.placeSellOrderFast(symbol, qty);
//                }
//            }
//        } else {
//        spotTrading.placeSellOrderFast(symbol, qty);
//        }
    }

    private boolean itsDealsAllowedPeriod(final LocalTime time) {
        return time.isBefore(eveningStopTime) && time.isAfter(nightStopTime);
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

    private void backupSellRecords() {
        dataService.saveAllSellRecords(marketData.getSellJournal().values());
    }

    private void backupOpenedPositions() {
        dataService.saveAllOpenedPositions(marketData.getLongPositions().values());
    }

    private void backupPreviousCandleData() {
        dataService.saveAllPreviousCandleData(marketData.getCachedCandlestickEvents().values().stream()
                .map(deque -> Optional.ofNullable(deque.peekFirst()))
                .filter(Optional::isPresent)
                .map(optional -> {
                    var prevEvent = optional.get();
                    return new PreviousCandleData("DAILY:" + prevEvent.getSymbol(), prevEvent);
                }).collect(Collectors.toSet())
        );
    }

    @PreDestroy
    public void destroy() {
        closeOpenedWebSocketStreams();
        backupSellRecords();
        backupOpenedPositions();
        backupPreviousCandleData();
    }
}
