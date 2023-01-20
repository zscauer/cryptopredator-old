package ru.tyumentsev.binancespotbot.strategy;

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
import org.springframework.stereotype.Service;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.binancespotbot.domain.PreviousCandleData;
import ru.tyumentsev.binancespotbot.domain.SellRecord;
import ru.tyumentsev.binancespotbot.mapping.CandlestickToEventMapper;
import ru.tyumentsev.binancespotbot.service.AccountManager;
import ru.tyumentsev.binancespotbot.service.DataService;
import ru.tyumentsev.binancespotbot.service.MarketInfo;
import ru.tyumentsev.binancespotbot.service.SpotTrading;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class Daily implements TradingStrategy {

    final MarketInfo marketInfo;
    final MarketData marketData;
    final SpotTrading spotTrading;
    final AccountManager accountManager;
    final DataService dataService;

    CandlestickInterval candlestickInterval;
    @Getter
    final Map<String, Closeable> candleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    final Map<String, Deque<CandlestickEvent>> cachedCandlestickEvents = new ConcurrentHashMap<>();
    final Map<String, SellRecord> sellJournal = new ConcurrentHashMap<>();
    final Map<String, Boolean> rocketCandidates;
    final LocalTime eveningStopTime = LocalTime.of(22, 0), nightStopTime = LocalTime.of(3, 5);

    @Value("${strategy.daily.enabled}")
    boolean dailyEnabled;
    @Value("${strategy.daily.averagingEnabled}")
    boolean averagingEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.candlestickEventsCacheSize}")
    int candlestickEventsCacheSize;
    @Value("${strategy.daily.volumeGrowthFactor}")
    double volumeGrowthFactor;
    @Value("${strategy.daily.priceGrowthFactor}")
    double priceGrowthFactor;
    @Value("${strategy.daily.candleTakeProfitFactor}")
    double candleTakeProfitFactor;
    @Value("${strategy.daily.pairTakeProfitFactor}")
    double pairTakeProfitFactor;
    @Value("${strategy.daily.priceDecreaseFactor}")
    double priceDecreaseFactor;
    @Value("${strategy.daily.takeProfitPriceDecreaseFactor}")
    double takeProfitPriceDecreaseFactor;
    @Value("${strategy.daily.averagingTriggerFactor}")
    double averagingTriggerFactor;
    @Value("${strategy.daily.rocketCandidatePercentageGrowth}")
    double rocketCandidatePercentageGrowth;

    @Override
    public boolean isEnabled() {
        return dailyEnabled;
    }

    @Override
    public void prepareData() {
        marketData.constructCandleStickEventsCache(tradingAsset, cachedCandlestickEvents);

        restoreCachedCandlestickEvents();

        cachedCandlestickEvents.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(entry -> {
                    var candlestick = marketInfo.getCandleSticks(entry.getKey(), CandlestickInterval.DAILY, 2).get(0);
                    return CandlestickToEventMapper.map(entry.getKey(), candlestick);
                }).filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(event -> marketData.addCandlestickEventToCache(event.getSymbol(), event, cachedCandlestickEvents)
                );

        log.info("[DAILY] prepared candlestick events of {} pairs.", cachedCandlestickEvents.values().stream().filter(value -> !value.isEmpty()).count());

        restoreSellJournalFromBackup();
        prepareOpenedLongPositions();
    }

    private void restoreSellJournalFromBackup() {
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

    private void restoreCachedCandlestickEvents() {
        int yesterdayDayOfYear = LocalDateTime.now().minusDays(1L).getDayOfYear();

        List<PreviousCandleData> dailyCachedCandleData = StreamSupport.stream(dataService.findAllPreviousCandleData().spliterator(), false).filter(data -> data.id().startsWith("Daily")).toList();
        log.debug("[DAILY] size of empty cached candlestick events before restoring from cache is: {}.",
                cachedCandlestickEvents.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).collect(Collectors.toSet()).size());

        dailyCachedCandleData.stream()
                .filter(data -> LocalDateTime.ofInstant(Instant.ofEpochMilli(data.event().getOpenTime()), ZoneId.systemDefault()).getDayOfYear() == yesterdayDayOfYear)
                .forEach(element -> marketData.addCandlestickEventToCache(element.event().getSymbol(), element.event(), cachedCandlestickEvents));

        var emptyCachedCandlestickEvents = cachedCandlestickEvents.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).collect(Collectors.toSet());
        log.debug("[DAILY] size of empty cached candlestick events after restoring from cache is: {} ({}).",
                emptyCachedCandlestickEvents.size(), emptyCachedCandlestickEvents);

        dataService.deleteAllPreviousCandleData(dailyCachedCandleData);
    }

    @Override
    public void handleBuying(final OrderTradeUpdateEvent buyEvent) {
        if (dailyEnabled
                && parsedDouble(buyEvent.getAccumulatedQuantity()) == parsedDouble(buyEvent.getOriginalQuantity())) {
            Optional.ofNullable(candleStickEventsStreams.remove(buyEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
                try {
                    candlestickEventsStream.close();
                } catch (IOException e) {
                    log.error("[DAILY] Error while trying to close candlestick event stream of '{}':\n{}", buyEvent.getSymbol(), e.getMessage());
                }
            });

            // if price == 0 most likely it was market order, use last market price.
            double dealPrice = parsedDouble(buyEvent.getPrice()) == 0
                    ? parsedDouble(marketInfo.getLastTickerPrice(buyEvent.getSymbol()).getPrice())
                    : parsedDouble(buyEvent.getPrice());

            log.info("[DAILY] BUY {} {} at {}.",
                    buyEvent.getOriginalQuantity(), buyEvent.getSymbol(), dealPrice);
            marketData.putLongPositionToPriceMonitoring(buyEvent.getSymbol(), dealPrice, parsedDouble(buyEvent.getOriginalQuantity()),
                    priceDecreaseFactor, Optional.ofNullable(rocketCandidates.remove(buyEvent.getSymbol())).orElse(false)
            );

            marketInfo.pairOrderFilled(buyEvent.getSymbol());

            candleStickEventsStreams.put(buyEvent.getSymbol(), marketInfo.openCandleStickEventsStream(buyEvent.getSymbol().toLowerCase(), candlestickInterval,
                    longPositionMonitoringCallback(buyEvent.getSymbol())));
        }
    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent sellEvent) {
        if (dailyEnabled
                && parsedDouble(sellEvent.getAccumulatedQuantity()) == (parsedDouble(sellEvent.getOriginalQuantity()))) {
            Optional.ofNullable(candleStickEventsStreams.remove(sellEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
                try {
                    candlestickEventsStream.close();
                } catch (IOException e) {
                    log.error("[DAILY] Error while trying to close candlestick event stream of '{}':\n{}", sellEvent.getSymbol(), e.getMessage());
                }
            });

            // if price == 0 most likely it was market order, use last market price.
            double dealPrice = parsedDouble(sellEvent.getPrice()) == 0
                    ? parsedDouble(marketInfo.getLastTickerPrice(sellEvent.getSymbol()).getPrice())
                    : parsedDouble(sellEvent.getPrice());

            log.info("[DAILY] SELL {} {} at {}.",
                    sellEvent.getOriginalQuantity(), sellEvent.getSymbol(), dealPrice);

            marketData.removeLongPositionFromPriceMonitoring(sellEvent.getSymbol());

            addSellRecordToJournal(sellEvent.getSymbol());
            marketInfo.pairOrderFilled(sellEvent.getSymbol());

            candleStickEventsStreams.put(sellEvent.getSymbol(), marketInfo.openCandleStickEventsStream(sellEvent.getSymbol().toLowerCase(), candlestickInterval,
                    marketMonitoringCallback(sellEvent.getSymbol())));
        }
    }

    public void startCandlstickEventsCacheUpdating(String asset, CandlestickInterval interval) {
        candlestickInterval = interval;
        closeOpenedWebSocketStreams();

        marketData.getCheapPairsExcludeOpenedPositions(asset)
                .forEach(ticker ->
                        candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                                marketMonitoringCallback(ticker))));
        marketData.getLongPositions().forEach((ticker, openedPosition) ->
                candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                        longPositionMonitoringCallback(ticker))));
    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback(String ticker) {
        return event -> {
            marketData.addCandlestickEventToCache(ticker, event, cachedCandlestickEvents);

            var currentEvent = cachedCandlestickEvents.get(ticker).getLast();
            var previousEvent = cachedCandlestickEvents.get(ticker).getFirst();

            var closePrice = parsedDouble(event.getClose());
            var openPrice = parsedDouble(event.getOpen());

            if (closePrice > openPrice * priceGrowthFactor
                    && (parsedDouble(currentEvent.getVolume()) > parsedDouble(previousEvent.getVolume()) * volumeGrowthFactor
                            || percentageDifference(closePrice, openPrice) > rocketCandidatePercentageGrowth) // if price grown a lot without volume, it might be a rocket.
                    && percentageDifference(parsedDouble(event.getHigh()), closePrice) < 4) { // candle's max price not much higher than current.
                buyFast(ticker, closePrice, tradingAsset, false);
                rocketCandidates.put(ticker, percentageDifference(closePrice, openPrice) > rocketCandidatePercentageGrowth
                        && !(parsedDouble(currentEvent.getVolume()) > parsedDouble(previousEvent.getVolume()) * volumeGrowthFactor));
            }
        };
    }

    private BinanceApiCallback<CandlestickEvent> longPositionMonitoringCallback(String ticker) {
        return event -> {
            marketData.addCandlestickEventToCache(ticker, event, cachedCandlestickEvents);

            List<AssetBalance> currentBalances = accountManager.getAccountBalances().stream()
                    .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();

            if (currentBalances.isEmpty()) {
                log.warn("[DAILY] No available trading assets found on binance account, but long position monitoring for '{}' is still executing.", ticker);
                return;
            }

            Optional.ofNullable(marketData.getLongPositions().get(ticker)).ifPresent(openedPosition -> {
                var currentPrice = parsedDouble(event.getClose());

                marketData.updateOpenedPosition(ticker, currentPrice, marketData.getLongPositions());
//                double currentPositionTakeProfitPriceDecreaseFactor = openedPosition.rocketCandidate() ?
//                        takeProfitPriceDecreaseFactor : 1D - (1D - takeProfitPriceDecreaseFactor) * 2;

//                if (assetPrice > parsedDouble(event.getOpen()) * candleTakeProfitFactor
//                        && openedPosition.priceDecreaseFactor() != currentPositionTakeProfitPriceDecreaseFactor) {
//                    openedPosition.priceDecreaseFactor(currentPositionTakeProfitPriceDecreaseFactor);
//                }
                if (currentPrice > openedPosition.avgPrice() * pairTakeProfitFactor) {
                    openedPosition.priceDecreaseFactor(takeProfitPriceDecreaseFactor);
                    if (averagingEnabled) {
//                        log.info("[DAILY] PRICE of {} GROWTH more than AVG ({}) and now equals {}.", ticker, openedPosition.avgPrice(), currentPrice);
                        buyFast(ticker, currentPrice, tradingAsset, true);
                    }
                }
                double stopTriggerValue = openedPosition.priceDecreaseFactor() == takeProfitPriceDecreaseFactor ? openedPosition.maxPrice() : openedPosition.avgPrice();

                if (currentPrice < stopTriggerValue * openedPosition.priceDecreaseFactor()) {
                    log.debug("[DAILY] PRICE of {} DECREASED and now equals {}.", ticker, currentPrice);
                    sellFast(ticker, openedPosition.qty(), tradingAsset);
//                } else if (averagingEnabled && currentPrice > openedPosition.avgPrice() * averagingTriggerFactor) {
//                    log.info("[DAILY] PRICE of {} GROWTH more than AVG ({}) and now equals {}.", ticker, openedPosition.avgPrice(), currentPrice);
////                    openedPosition.priceDecreaseFactor(1D - (1D - pairTakeProfitFactor) / 2);
//                    buyFast(ticker, currentPrice, tradingAsset);
                }
            });
        };
    }

    private void buyFast(String symbol, double price, String quoteAsset, boolean itsAveraging) {
        if ((itsDealsAllowedPeriod(LocalTime.now()) || itsAveraging) &&
                !(marketInfo.pairOrderIsProcessing(symbol) || thisSignalWorkedOutBefore(symbol))) {
            log.debug("[DAILY] price of {} growth more than {}%, and now equals {}.", symbol, Double.valueOf(100 * priceGrowthFactor - 100).intValue(), price);
            marketInfo.pairOrderPlaced(symbol);
            spotTrading.placeBuyOrderFast(symbol, price, quoteAsset, accountManager);
        }
    }

    private void sellFast(String symbol, double qty, String quoteAsset) {
        if (!marketInfo.pairOrderIsProcessing(symbol)) {
            spotTrading.placeSellOrderFast(symbol, qty);
        }

//        if (matchTrend) {
//            List<Candlestick> candleSticks = marketInfo.getCandleSticks(symbol, CandlestickInterval.DAILY, 2);
//            if (marketInfo.pairHadTradesInThePast(candleSticks, 2)) {
//                // current price higher then close price of previous day more than rocketFactor
//                // - there is rocket.
//                if (parsedDouble(candleSticks.get(1).getClose()) > parsedDouble(candleSticks.get(0).getClose())
//                        * rocketFactor) {
//                    spotTrading.placeSellOrderFast(symbol, qty);
//                } else if (parsedDouble(candleSticks.get(0).getClose()) > parsedDouble(candleSticks.get(1).getClose())
//                        * priceGrowthFactor) { // close price of previous day is higher than current more than growth
//                    // factor - there is downtrend.
//                    spotTrading.placeSellOrderFast(symbol, qty);
//                }
//            }
//        } else {
//        spotTrading.placeSellOrderFast(symbol, qty);
//        }
    }

    private void addSellRecordToJournal(String pair) {
        sellJournal.put(pair, new SellRecord(pair, LocalDateTime.now()));
    }

    private boolean thisSignalWorkedOutBefore(final String pair) {
        AtomicBoolean ignoreSignal = new AtomicBoolean(false);

        Optional.ofNullable(sellJournal.get(pair)).ifPresent(sellRecord -> {
            if (sellRecord.sellTime().getDayOfYear() == LocalDateTime.now().getDayOfYear()) {
                ignoreSignal.set(true);
            } else {
                log.info("[DAILY] Period of signal ignoring for {} expired, remove pair from sell journal.", pair);
                sellJournal.remove(pair);
            }
        });

        return ignoreSignal.get();
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
        dataService.saveAllSellRecords(sellJournal.values());
    }

    private void backupOpenedPositions() {
        dataService.saveAllOpenedPositions(marketData.getLongPositions().values());
    }

    private void backupPreviousCandleData() {
        dataService.saveAllPreviousCandleData(cachedCandlestickEvents.values().stream()
                .map(deque -> Optional.ofNullable(deque.peekFirst()))
                .filter(Optional::isPresent)
                .map(optional -> {
                    var prevEvent = optional.get();
                    return new PreviousCandleData("Daily:" + prevEvent.getSymbol(), prevEvent);
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
