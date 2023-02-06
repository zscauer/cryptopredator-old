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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.PreviousCandleContainer;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.mapping.CandlestickToEventMapper;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.cache.DailyVolumesStrategyCondition;

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
    final DailyVolumesStrategyCondition dailyVolumesStrategyCondition;
    final SpotTrading spotTrading;
    final DataService dataService;

    final CandlestickInterval candlestickInterval = CandlestickInterval.DAILY;
    @Getter
    final Map<String, Deque<CandlestickEvent>> cachedCandlestickEvents = new ConcurrentHashMap<>();
    @Getter
    final Map<String, Closeable> candleStickEventsStreams = new ConcurrentHashMap<>();

    final Map<String, Boolean> rocketCandidates;
    final LocalTime eveningStopTime = LocalTime.of(22, 0), nightStopTime = LocalTime.of(3, 5);

    final static String STRATEGY_NAME = "dailyvolumes";
    final static Integer STRATEGY_ID = 1001;

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.daily.enabled}")
    boolean dailyEnabled;
    @Value("${strategy.global.candlestickEventsCacheSize}")
    int candlestickEventsCacheSize;
    @Value("${strategy.daily.averagingEnabled}")
    boolean averagingEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.minimalAssetBalance}")
    int minimalAssetBalance;
    @Value("${strategy.global.baseOrderVolume}")
    int baseOrderVolume;
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

    @Scheduled(fixedDelayString = "${strategy.daily.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.daily.startCandlstickEventsCacheUpdating.initialDelay}")
    public void daily_startCandlstickEventsCacheUpdating() {
        if (dailyEnabled && !testLaunch) {
            startCandlstickEventsCacheUpdating();
//            Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).forEach(thread -> {
//                log.info("Thread {} name: {} group: {}.", thread.getId(), thread.getName(), thread.getThreadGroup());
//            });
        }
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public Integer getId() {
        return STRATEGY_ID;
    }

    @Override
    public boolean isEnabled() {
        return dailyEnabled;
    }

    @Override
    public void prepareData() {

        constructCandleStickEventsCache();

        try {
            restoreCachedCandlestickEvents("DAILY");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
//        var cachedCandlestickEvents = marketData.getCachedCandlestickEvents();

        cachedCandlestickEvents.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(entry -> {
                    var candlestick = marketInfo.getCandleSticks(entry.getKey(), CandlestickInterval.DAILY, 2).get(0);
                    return CandlestickToEventMapper.map(entry.getKey(), candlestick);
                }).filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(event -> addCandlestickEventToCache(event.getSymbol(), event)
                );

        log.info("Prepared candlestick events of {} pairs.", cachedCandlestickEvents.values().stream().filter(value -> !value.isEmpty()).count());

            restoreSellJournalFromCache();
            prepareOpenedLongPositions();
    }

    private void constructCandleStickEventsCache() {
        Optional.ofNullable(marketInfo.getCheapPairs().get(tradingAsset)).ifPresentOrElse(list -> {
            list.forEach(pair -> {
                cachedCandlestickEvents.put(pair, new LinkedList<>());
            });
            log.debug("Cache of candle stick events constructed with {} elements.", cachedCandlestickEvents.size());
        }, () -> {
            log.warn("Can't construct queues of candlestick events cache for {} - list of cheap pairs for this asset is empty.", tradingAsset);
        });
    }

    private void restoreSellJournalFromCache() {
        var sellJournal = dailyVolumesStrategyCondition.getSellJournal();
        dataService.findAllSellRecords(this)
                .forEach(record -> sellJournal.put(record.symbol(), record));
        dataService.deleteAllSellRecords(sellJournal.values(), this);
    }

    private void prepareOpenedLongPositions() {
        List<String> accountPositions = spotTrading.recieveOpenedLongPositionsFromMarket().stream()
                .map(assetBalance -> assetBalance.getAsset() + tradingAsset).toList();

        List<OpenedPosition> cachedOpenedPositions =  dataService.findAllOpenedPositions(this);
        log.debug("Found next cached opened positions: {}", cachedOpenedPositions);
                cachedOpenedPositions.forEach(pos -> {
                    if (accountPositions.contains(pos.symbol())) {
                        dailyVolumesStrategyCondition.getLongPositions().put(pos.symbol(), pos);
                    }
                });

        dataService.deleteAllOpenedPositions(dailyVolumesStrategyCondition.getLongPositions().values(), this);
    }

    private void restoreCachedCandlestickEvents(String interval) throws IOException {
        int yesterdayDayOfYear = LocalDateTime.now().minusDays(1L).getDayOfYear();
//        var cachedCandlestickEvents = marketData.getCachedCandlestickEvents();

        List<PreviousCandleContainer> dailyCachedCandleData = dataService.findAllPreviousCandleContainers().stream().filter(data -> data.id().startsWith(interval)).toList();
        log.debug("Size of empty cached candlestick events before restoring from cache is: {}.",
                cachedCandlestickEvents.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).collect(Collectors.toSet()).size());

        dailyCachedCandleData.stream()
                .filter(data -> LocalDateTime.ofInstant(Instant.ofEpochMilli(data.event().getOpenTime()), ZoneId.systemDefault()).getDayOfYear() == yesterdayDayOfYear)
                .forEach(element -> addCandlestickEventToCache(element.event().getSymbol(), element.event()));

        var emptyCachedCandlestickEvents = cachedCandlestickEvents.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).collect(Collectors.toSet());
        log.debug("Size of empty cached candlestick events after restoring from cache is: {} ({}).",
                emptyCachedCandlestickEvents.size(), emptyCachedCandlestickEvents);

        dataService.deleteAllPreviousCandleContainers(new ArrayList<>(dailyCachedCandleData));
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

    @Override
    public void handleBuying(final OrderTradeUpdateEvent buyEvent) {
        log.debug("Get buy event of {} with strategy id {}", buyEvent.getSymbol(), buyEvent.getStrategyId());
        if (dailyEnabled && getId().equals(buyEvent.getStrategyId())
                && parsedFloat(buyEvent.getAccumulatedQuantity()) == parsedFloat(buyEvent.getOriginalQuantity())) {
            final String symbol = buyEvent.getSymbol();

//            Optional.ofNullable(candleStickEventsStreams.remove(symbol)).ifPresent(candlestickEventsStream -> {
//                try {
//                    candlestickEventsStream.close();
//                    log.debug("Candlestick events stream of {} closed in handle buying.", symbol);
//                } catch (IOException e) {
//                    log.error("Error while trying to close candlestick events stream of '{}':\n{}", symbol, e.getMessage());
//                }
//            });

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(buyEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(symbol).getPrice())
                    : parsedFloat(buyEvent.getPrice());

            log.info("BUY {} {} at {}.",
                    buyEvent.getAccumulatedQuantity(), symbol, dealPrice);
            dailyVolumesStrategyCondition.putLongPositionToPriceMonitoring(symbol, dealPrice, parsedFloat(buyEvent.getAccumulatedQuantity()),
                    priceDecreaseFactor, Optional.ofNullable(rocketCandidates.remove(symbol)).orElse(false), getName()
            );

//            candleStickEventsStreams.put(symbol, marketInfo.openCandleStickEventsStream(symbol.toLowerCase(), candlestickInterval,
//                    longPositionMonitoringCallback()));

            marketInfo.pairOrderFilled(symbol, getName());
        }
    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent sellEvent) {
        log.debug("Get sell event of {} with strategy id {}.", sellEvent.getSymbol(), sellEvent.getStrategyId());
        if (dailyEnabled && (getId().equals(Optional.ofNullable(sellEvent.getStrategyId()).orElse(getId())))
                && parsedFloat(sellEvent.getAccumulatedQuantity()) == (parsedFloat(sellEvent.getOriginalQuantity()))) {
//            Optional.ofNullable(candleStickEventsStreams.remove(sellEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
//                try {
//                    candlestickEventsStream.close();
//                    log.debug("Candlestick events stream of {} closed in handle selling.", sellEvent.getSymbol());
//                } catch (IOException e) {
//                    log.error("Error while trying to close candlestick event stream of '{}':\n{}", sellEvent.getSymbol(), e.getMessage());
//                }
//            });

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(sellEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(sellEvent.getSymbol()).getPrice())
                    : parsedFloat(sellEvent.getPrice());

            log.info("SELL {} {} at {}.",
                    sellEvent.getOriginalQuantity(), sellEvent.getSymbol(), dealPrice);

            dailyVolumesStrategyCondition.removeLongPositionFromPriceMonitoring(sellEvent.getSymbol());
            dailyVolumesStrategyCondition.addSellRecordToJournal(sellEvent.getSymbol(), getName());

//            candleStickEventsStreams.put(sellEvent.getSymbol(), marketInfo.openCandleStickEventsStream(sellEvent.getSymbol().toLowerCase(), candlestickInterval,
//                    marketMonitoringCallback()));

            marketInfo.pairOrderFilled(sellEvent.getSymbol(), getName());
        }
    }

    public void startCandlstickEventsCacheUpdating() {
        closeOpenedWebSocketStreams();
        AtomicInteger marketMonitoringThreadsCounter = new AtomicInteger();
//        AtomicInteger longMonitoringThreadsCounter = new AtomicInteger();

        marketInfo.getCheapPairs().getOrDefault(tradingAsset, Collections.emptyList()).forEach(ticker -> {
            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    marketMonitoringCallback()));
            marketMonitoringThreadsCounter.getAndIncrement();
        });

//        marketInfo.getCheapPairsExcludeOpenedPositions(tradingAsset, dailyVolumesStrategyCondition.getLongPositions().keySet(), dailyVolumesStrategyCondition.getShortPositions().keySet()).forEach(ticker -> {
//            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
//                    marketMonitoringCallback()));
//            marketMonitoringThreadsCounter.getAndIncrement();
//        });
//        dailyVolumesStrategyCondition.getLongPositions().forEach((ticker, openedPosition) -> {
//            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
//                    longPositionMonitoringCallback()));
//            longMonitoringThreadsCounter.getAndIncrement();
//        });

        log.info("Runned {} market monitoring threads.", marketMonitoringThreadsCounter);
    }

//    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback() {
//        return event -> {
//            final String ticker = event.getSymbol();
//            addCandlestickEventToCache(ticker, event);
//
//            var currentEvent = cachedCandlestickEvents.get(ticker).getLast();
//            var previousEvent = cachedCandlestickEvents.get(ticker).getFirst();
//
//            var closePrice = parsedFloat(event.getClose());
//            var openPrice = parsedFloat(event.getOpen());
//
//            if (closePrice > openPrice * priceGrowthFactor
//                    && (parsedFloat(currentEvent.getVolume()) > parsedFloat(previousEvent.getVolume()) * volumeGrowthFactor)
//                    && percentageDifference(parsedFloat(event.getHigh()), closePrice) < 4) { // candle's max price not much higher than current.
//                buyFast(ticker, closePrice, tradingAsset, false);
////                rocketCandidates.put(ticker, percentageDifference(closePrice, openPrice) > rocketCandidatePercentageGrowth
////                        && !(parsedFloat(currentEvent.getVolume()) > parsedFloat(previousEvent.getVolume()) * volumeGrowthFactor));
//            }
//        };
//    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback() {
        return event -> {
            addCandlestickEventToCache(event.getSymbol(), event);

//            List<AssetBalance> currentBalances = spotTrading.getAccountBalances().stream()
//                    .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();

//            if (currentBalances.isEmpty()) {
//                log.warn("No available trading assets found on binance account, but long position monitoring for '{}' is still executing.", event.getSymbol());
//                return;
//            }

            Optional.ofNullable(dailyVolumesStrategyCondition.getLongPositions().get(event.getSymbol())).ifPresentOrElse(
                    openedPosition -> analizeOpenedPosition(event, openedPosition),
                    () -> analizeMarketPosition(event));
        };
    }

    private void analizeMarketPosition(final CandlestickEvent event) {
        final String ticker = event.getSymbol();
        addCandlestickEventToCache(ticker, event);

        var currentEvent = cachedCandlestickEvents.get(ticker).getLast();
        var previousEvent = cachedCandlestickEvents.get(ticker).getFirst();

        var closePrice = parsedFloat(event.getClose());
        var openPrice = parsedFloat(event.getOpen());

        if (closePrice > openPrice * priceGrowthFactor
                && (parsedFloat(currentEvent.getVolume()) > parsedFloat(previousEvent.getVolume()) * volumeGrowthFactor)
                && percentageDifference(parsedFloat(event.getHigh()), closePrice) < 4) { // candle's max price not much higher than current.
            buyFast(ticker, closePrice, tradingAsset, false);
//                rocketCandidates.put(ticker, percentageDifference(closePrice, openPrice) > rocketCandidatePercentageGrowth
//                        && !(parsedFloat(currentEvent.getVolume()) > parsedFloat(previousEvent.getVolume()) * volumeGrowthFactor));
        }
    }

    private void analizeOpenedPosition(final CandlestickEvent event, final OpenedPosition openedPosition) {
        final String ticker = event.getSymbol();
        var currentPrice = parsedFloat(event.getClose());

        dailyVolumesStrategyCondition.updateOpenedPositionLastPrice(ticker, currentPrice, dailyVolumesStrategyCondition.getLongPositions());

        if (currentPrice > openedPosition.avgPrice() * pairTakeProfitFactor) {
            log.debug("Current price decrease factor of {} is {}.", openedPosition.symbol(), dailyVolumesStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());
            openedPosition.priceDecreaseFactor(takeProfitPriceDecreaseFactor);
            log.debug("Price decrease factor of {} after changing is {}.", openedPosition.symbol(), dailyVolumesStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());

            if (averagingEnabled) {
                buyFast(ticker, currentPrice, tradingAsset, true);
            }
        }
        float stopTriggerValue = openedPosition.priceDecreaseFactor() == takeProfitPriceDecreaseFactor ? openedPosition.maxPrice() : openedPosition.avgPrice();

        if (currentPrice < stopTriggerValue * openedPosition.priceDecreaseFactor()) {
            log.info("PRICE of {} DECREASED and now equals {}, price decrease factor is {} / {}.",
                    ticker, currentPrice, openedPosition.priceDecreaseFactor(), dailyVolumesStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());
            sellFast(ticker, openedPosition.qty(), tradingAsset);
        }
    }

    private void buyFast(final String symbol, final float price, String quoteAsset, boolean itsAveraging) {
        if ((itsDealsAllowedPeriod(LocalTime.now()) || itsAveraging) &&
                !(marketInfo.pairOrderIsProcessing(symbol, getName()) || dailyVolumesStrategyCondition.thisSignalWorkedOutBefore(symbol))) {
            log.debug("Price of {} growth more than {}%, and now equals {}.", symbol, Float.valueOf(100 * priceGrowthFactor - 100).intValue(), price);
            spotTrading.placeBuyOrderFast(symbol, getName(), getId(), price, quoteAsset, minimalAssetBalance, baseOrderVolume);
        }
    }

    private void sellFast(String symbol, float qty, String quoteAsset) {
        if (!marketInfo.pairOrderIsProcessing(symbol, getName())) {
            spotTrading.placeSellOrderFast(symbol, getName(), getId(), qty);
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
        dataService.saveAllSellRecords(dailyVolumesStrategyCondition.getSellJournal().values(), this);
    }

    private void backupOpenedPositions() {
        List<OpenedPosition> savedPositions = dataService.saveAllOpenedPositions(dailyVolumesStrategyCondition.getLongPositions().values(), this);
    }

    private void backupPreviousCandleContainers() {
        dataService.saveAllPreviousCandleContainers(cachedCandlestickEvents.values().stream()
                .map(deque -> Optional.ofNullable(deque.peekFirst()))
                .filter(Optional::isPresent)
                .map(optional -> {
                    var prevEvent = optional.get();
                    return new PreviousCandleContainer("DAILY:" + prevEvent.getSymbol(), prevEvent);
                }).collect(Collectors.toSet())
        );
    }

    @PreDestroy
    public void destroy() {
        try {
            closeOpenedWebSocketStreams();
            backupPreviousCandleContainers();
            backupOpenedPositions();
            backupSellRecords();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
