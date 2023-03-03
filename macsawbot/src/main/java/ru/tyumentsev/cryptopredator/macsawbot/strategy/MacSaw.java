package ru.tyumentsev.cryptopredator.macsawbot.strategy;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.Candle;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.market.CandlestickInterval;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.DoubleNum;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.mapping.CandlestickToBaseBarMapper;
import ru.tyumentsev.cryptopredator.commons.service.BotStateService;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;
import ru.tyumentsev.cryptopredator.macsawbot.cache.MacSawStrategyCondition;

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
@Slf4j
public class MacSaw implements TradingStrategy {

    final MarketInfo marketInfo;
    final MacSawStrategyCondition macSawStrategyCondition;
    final SpotTrading spotTrading;
    final DataService dataService;
    final BotStateService botStateService;


    final CandlestickInterval marketCandlestickInterval = CandlestickInterval.FIFTEEN_MINUTES;
    final CandlestickInterval openedPositionsCandlestickInterval = CandlestickInterval.FIFTEEN_MINUTES;
    final int baseBarSeriesLimit = 26;

    @Getter
    final Map<String, Closeable> marketCandleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    final Map<String, Closeable> openedPositionsCandleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    final Map<String, BaseBarSeries> marketBarSeriesMap = new ConcurrentHashMap<>();
    @Getter
    final Map<String, BaseBarSeries> openedPositionsBarSeriesMap = new ConcurrentHashMap<>();

    final BaseBarSeriesBuilder barSeriesBuilder = new BaseBarSeriesBuilder();
    final static String STRATEGY_NAME = "macsaw";
    final static Integer STRATEGY_ID = 1003;
    final static String USER_DATA_UPDATE_ENDPOINT = "http://macsawbot:8080/state/userDataUpdateEvent";

    final Map<String, AtomicBoolean> emulatedPositions = new ConcurrentHashMap<>();

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.macSaw.enabled}")
    boolean macSawEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.macSaw.ordersQtyLimit}")
    int ordersQtyLimit;
    @Value("${strategy.global.minimalAssetBalance}")
    int minimalAssetBalance;
    @Value("${strategy.global.baseOrderVolume}")
    int baseOrderVolume;
    @Value("${strategy.macSaw.priceDecreaseFactor}")
    float priceDecreaseFactor;

    @Scheduled(fixedDelayString = "${strategy.macSaw.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.macSaw.startCandlstickEventsCacheUpdating.initialDelay}")
    public void macSaw_startCandlstickEventsCacheUpdating() {
        if (macSawEnabled && !testLaunch) {
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
        return macSawEnabled;
    }

    @Override
    public void prepareData() {
        restoreSellJournalFromCache();
        prepareOpenedLongPositions();
        defineAvailableOrdersLimit();
        subscribeToUserUpdateEvents();
    }

    private void restoreSellJournalFromCache() {
        var sellJournal = macSawStrategyCondition.getSellJournal();
        dataService.findAllSellRecords(this)
                .forEach(record -> sellJournal.put(record.symbol(), record));
        dataService.deleteAllSellRecords(sellJournal.values(), this);
    }

    private void prepareOpenedLongPositions() {
        List<String> accountPositions = spotTrading.recieveOpenedLongPositionsFromMarket().stream()
                .map(assetBalance -> assetBalance.getAsset() + tradingAsset).toList();

        List<OpenedPosition> cachedOpenedPositions = dataService.findAllOpenedPositions(this);
        log.info("Found next cached opened positions: {}", cachedOpenedPositions);
        cachedOpenedPositions.forEach(pos -> {
            if (accountPositions.contains(pos.symbol())) {
                pos.threadName(null)
                        .updateStamp(null);
                macSawStrategyCondition.getLongPositions().put(pos.symbol(), pos);
            }
        });

        dataService.deleteAllOpenedPositions(macSawStrategyCondition.getLongPositions().values(), this);
    }

    private void defineAvailableOrdersLimit() {
        int availableOrdersLimit = ordersQtyLimit - macSawStrategyCondition.getLongPositions().values().stream()
                .map(openedPosition -> Math.ceil(openedPosition.avgPrice() * openedPosition.qty()))
                .reduce(0D, Double::sum)
                .intValue() / baseOrderVolume;

        botStateService.setAvailableOrdersLimit(getId(), availableOrdersLimit, baseOrderVolume);
    }

    private void subscribeToUserUpdateEvents() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("strategyId", getId().toString());
        parameters.put("botAddress", USER_DATA_UPDATE_ENDPOINT);
        botStateService.addActiveStrategy(parameters);
    }

    @Override
    public void handleBuying(final OrderTradeUpdateEvent buyEvent) {
        log.debug("Get buy event of {} with strategy id {}", buyEvent.getSymbol(), buyEvent.getStrategyId());
        if (macSawEnabled && getId().equals(buyEvent.getStrategyId())) {
            final String symbol = buyEvent.getSymbol();

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(buyEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(symbol).getPrice())
                    : parsedFloat(buyEvent.getPrice());

            log.info("BUY {} {} at {}.",
                    buyEvent.getAccumulatedQuantity(), symbol, dealPrice);
            macSawStrategyCondition.addOpenedPosition(symbol, dealPrice, parsedFloat(buyEvent.getAccumulatedQuantity()),
                    priceDecreaseFactor, false, getName()
            );

            marketInfo.pairOrderFilled(symbol, getName());

            // + new logic
            Optional.ofNullable(openedPositionsCandleStickEventsStreams.get(symbol)).ifPresentOrElse(stream -> {
            }, () -> { // do nothing if stream is already running.
                openedPositionsCandleStickEventsStreams.put(symbol,
                        marketInfo.openCandleStickEventsStream(symbol.toLowerCase(), openedPositionsCandlestickInterval,
                                openedPositionMonitoringCallback())
                );
            });
            // - new logic
        }
    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent sellEvent) {
        final String symbol = sellEvent.getSymbol();
        log.debug("Get sell event of {} with strategy id {}.", symbol, sellEvent.getStrategyId());

        if (macSawEnabled && (getId().equals(Optional.ofNullable(sellEvent.getStrategyId()).orElse(getId())))) {

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(sellEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(symbol).getPrice())
                    : parsedFloat(sellEvent.getPrice());

            log.info("SELL {} {} at {}.",
                    sellEvent.getOriginalQuantity(), symbol, dealPrice);

            macSawStrategyCondition.removeOpenedPosition(symbol);
            macSawStrategyCondition.addSellRecordToJournal(symbol, getName());

            // + new logic
            Optional.ofNullable(openedPositionsCandleStickEventsStreams.remove(symbol)).ifPresentOrElse(stream -> {
                try {
                    stream.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }, () -> {
                log.warn("Sell event of {} recieved, but have no opened position monitoring stream.", symbol);
            });
            openedPositionsBarSeriesMap.remove(symbol);
            // - new logic

            marketInfo.pairOrderFilled(symbol, getName());
        }
    }

    public void startCandlstickEventsCacheUpdating() {
        closeOpenedWebSocketStreams();
        AtomicInteger marketMonitoringThreadsCounter = new AtomicInteger();
        AtomicInteger openedPositionsMonitoringThreadsCounter = new AtomicInteger();

        Deque<String> marketTickers = marketInfo.getCheapPairs().get(tradingAsset).stream().sorted().collect(Collectors.toCollection(LinkedList::new));

        var streamsCount = marketTickers.size() / 2 + marketTickers.size() % 2;
        List<List<String>> combinedPairsList = new ArrayList<>();
        for (int i = streamsCount; i > 0; i--) {
            List<String> combinedPair = new ArrayList<>();
            if (!marketTickers.isEmpty()) {
                combinedPair.add(marketTickers.poll());
            }
            if (!marketTickers.isEmpty()) {
                combinedPair.add(marketTickers.poll());
            }
            if (!combinedPair.isEmpty()) {
                combinedPairsList.add(combinedPair);
            }
        }

        combinedPairsList.forEach(combinedPair -> {
            marketCandleStickEventsStreams.put(String.join(",", combinedPair),
                    marketInfo.openCandleStickEventsStream(String.join(",", combinedPair).toLowerCase(), marketCandlestickInterval,
                            marketMonitoringCallback()));
            marketMonitoringThreadsCounter.getAndIncrement();
        });

        macSawStrategyCondition.getLongPositions().keySet().forEach(symbol -> {
            openedPositionsCandleStickEventsStreams.put(symbol,
                    marketInfo.openCandleStickEventsStream(symbol.toLowerCase(), openedPositionsCandlestickInterval,
                            openedPositionMonitoringCallback()));
            openedPositionsMonitoringThreadsCounter.getAndIncrement();
        });

        log.info("Runned {} market monitoring threads and {} opened positions monitoring threads.", marketMonitoringThreadsCounter, openedPositionsMonitoringThreadsCounter);
    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback() {
        return event -> {
            addEventToBaseBarSeries(event, marketBarSeriesMap, marketCandlestickInterval);

            Optional.ofNullable(macSawStrategyCondition.getLongPositions().get(event.getSymbol())).ifPresentOrElse(
                    openedPosition -> {},//analizeOpenedPosition(event, openedPosition), // ignore opened positions
                    () -> analizeMarketPosition(event));
        };
    }

    private BinanceApiCallback<CandlestickEvent> openedPositionMonitoringCallback() {
        return event -> {
            addEventToBaseBarSeries(event, openedPositionsBarSeriesMap, openedPositionsCandlestickInterval);

//            Optional.ofNullable(emulatedPositions.get(event.getSymbol())).ifPresent(
//                    inPos -> { if (inPos.get()) {
//                        analizeOpenedPosition(event, null);
//                    }
//            });
            // TODO: replace in production
            Optional.ofNullable(macSawStrategyCondition.getLongPositions().get(event.getSymbol())).ifPresent(
                    openedPosition -> analizeOpenedPosition(event, openedPosition));
        };
    }

    private void analizeMarketPosition(final CandlestickEvent event) {
        if (signalToOpenLongPosition(event)) {
            emulateBuy(event.getSymbol(), parsedFloat(event.getClose()));
//            buyFast(event.getSymbol(), parsedFloat(event.getClose()), tradingAsset, false);
        }
    }

    private boolean signalToOpenLongPosition(final CandlestickEvent event) {
        if (Optional.ofNullable(emulatedPositions.get(event.getSymbol()))
                .map(AtomicBoolean::get)
                .orElse(false)
        ) {
            return false;
        }
//        if (marketInfo.pairOrderIsProcessing(event.getSymbol(), getName())) { //|| macSawStrategyCondition.thisSignalWorkedOutBefore(event.getSymbol())) {
//            return false;
//        }
        BaseBarSeries series = Optional.ofNullable(marketBarSeriesMap.get(event.getSymbol())).orElseGet(BaseBarSeries::new);
        if (series.getBarData().isEmpty()) {
            return false;
        }

        var endBarSeriesIndex = series.getEndIndex();
        // define trend by MACD (s8,l16,sig6).
        MACDIndicator macdTrendIndicator = new MACDIndicator(new ClosePriceIndicator(series), 8, 16);
        float currentMacdValue = macdTrendIndicator.getValue(endBarSeriesIndex).floatValue();
        float previousMacdValue = macdTrendIndicator.getValue(endBarSeriesIndex - 1).floatValue();
        float currrentSignalLineValue = macdSignalLineValue(macdTrendIndicator, endBarSeriesIndex, 6);
        float previousSignalLineValue = macdSignalLineValue(macdTrendIndicator, endBarSeriesIndex - 1, 6);
        if (currentMacdValue > previousMacdValue && currentMacdValue > currrentSignalLineValue && previousMacdValue > previousSignalLineValue) { // uptrend
//            SMAIndicator sma3high = new SMAIndicator(new HighPriceIndicator(series), 3);
            SMAIndicator sma3low = new SMAIndicator(new LowPriceIndicator(series), 3);
            // current price is close to avg low.
            // MACD should be positive?
            // прошлая свеча была бычьей
            // и коснулась своей ema3low
            // и текущая цена выше хая прошлой свечи
            // и лоу текущей свечи выше лоу предыдущей
            float currentPrice = parsedFloat(event.getClose());
            var previousBar = series.getBar(endBarSeriesIndex - 1);
            if (previousBar.isBullish()
                    && previousBar.getLowPrice().isLessThanOrEqual(sma3low.getValue(endBarSeriesIndex - 1))
                    && previousBar.getHighPrice().isLessThan(DoubleNum.valueOf(currentPrice))
                    && previousBar.getLowPrice().isLessThan(series.getBar(endBarSeriesIndex).getLowPrice())) {
                return true;
            }

//            if (sma3low.getValue(endBarSeriesIndex - 1).isGreaterThanOrEqual(DoubleNum.valueOf(currentPrice))) {
//                return true;
//            }
        }
        return false;
    }

    private void analizeOpenedPosition(final CandlestickEvent event, final OpenedPosition openedPosition) {
        final String symbol = event.getSymbol();
        var currentPrice = parsedFloat(event.getClose());

        macSawStrategyCondition.updateOpenedPositionLastPrice(symbol, currentPrice, macSawStrategyCondition.getLongPositions());

//        if (currentPrice > openedPosition.avgPrice() * pairTakeProfitFactor && !openedPosition.priceDecreaseFactor().equals(takeProfitPriceDecreaseFactor)) {
//            openedPosition.priceDecreaseFactor(takeProfitPriceDecreaseFactor);
//        }

//        if (averagingEnabled && currentPrice > openedPosition.avgPrice() * averagingTrigger) {
//            buyFast(symbol, currentPrice, tradingAsset, true);
//        }

        if (signalToCloseLongPosition(event, openedPosition)) {
            emulateSell(event.getSymbol(), currentPrice);
//            sellFast(event.getSymbol(), openedPosition.qty(), tradingAsset);
        }
    }

    private void buyFast(final String symbol, final float price, String quoteAsset, boolean itsAveraging) {
        if (!(marketInfo.pairOrderIsProcessing(symbol, getName()))) { //|| macSawStrategyCondition.thisSignalWorkedOutBefore(symbol))) {
//            emulateBuy(symbol, price);
//            log.debug("Price of {} growth more than {}%, and now equals {}.", symbol, Float.valueOf(100 * priceGrowthFactor - 100).intValue(), price);
            spotTrading.placeBuyOrderFast(symbol, getName(), getId(), price, quoteAsset, minimalAssetBalance, baseOrderVolume);
        }
    }

    private boolean signalToCloseLongPosition(final CandlestickEvent event, final OpenedPosition openedPosition) {
        if (!Optional.ofNullable(emulatedPositions.get(event.getSymbol()))
                .map(AtomicBoolean::get)
                .orElse(false)
        ) {
            return false;
        }
//        if (marketInfo.pairOrderIsProcessing(event.getSymbol(), getName())) {
//            return false;
//        }

        BaseBarSeries series = Optional.ofNullable(openedPositionsBarSeriesMap.get(event.getSymbol())).orElseGet(BaseBarSeries::new);
        if (series.getBarData().isEmpty()) {
            return false;
        }

        var endBarSeriesIndex = series.getEndIndex();
        // define trend by MACD (s8,l16,sig6).
//        MACDIndicator macdTrendIndicator = new MACDIndicator(new ClosePriceIndicator(series), 8, 16);
//        float currentMacdValue = macdTrendIndicator.getValue(endBarSeriesIndex).floatValue();
//        float previousMacdValue = macdTrendIndicator.getValue(endBarSeriesIndex - 1).floatValue();
//        float currrentSignalLineValue = macdSignalLineValue(macdTrendIndicator, endBarSeriesIndex, 6);
//        float previousSignalLineValue = macdSignalLineValue(macdTrendIndicator, endBarSeriesIndex - 1, 6);
//        if (currentMacdValue > previousMacdValue && currentMacdValue > currrentSignalLineValue && previousMacdValue > previousSignalLineValue) { // uptrend
            SMAIndicator sma3high = new SMAIndicator(new HighPriceIndicator(series), 3);
//            SMAIndicator sma3low = new SMAIndicator(new LowPriceIndicator(series), 3);
            // current price is close to avg low.
        float currentPrice = parsedFloat(event.getClose());
        // set minimal profit (2%)?
        if (currentPrice > openedPosition.avgPrice() * 1.02
            && sma3high.getValue(endBarSeriesIndex).isLessThanOrEqual(DoubleNum.valueOf(currentPrice))) {
            return true;
        }
//        }

        return false;
    }

    private float macdSignalLineValue(final MACDIndicator macdIndicator, final int endBarSeriesIndex, final int signalLineLehgth) {
        float macd9barsAVG = 0F;
        for (int i = endBarSeriesIndex; i > endBarSeriesIndex - signalLineLehgth; i--) { // signal line
            macd9barsAVG += macdIndicator.getValue(i).floatValue();
        }
        return  macd9barsAVG / signalLineLehgth;
    }

    public void addEventToBaseBarSeries(final CandlestickEvent event, final Map<String, BaseBarSeries> barSeriesMap, final CandlestickInterval candlestickInterval) {
//        BaseBarSeries barSeries = barSeriesMap.get(event.getSymbol());
//        if (barSeries != null && barSeries.getEndIndex() >= 0) {
//
//                if (ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getCloseTime()), ZoneId.systemDefault()).equals(barSeries.getBar(barSeries.getEndIndex()).getEndTime())) {
//                    barSeries.addBar(CandlestickToBaseBarMapper.map(event, candlestickInterval), true);
//                } else {
//                    var mapperBar = CandlestickToBaseBarMapper.map(event, candlestickInterval);
//                    try {
//                        barSeries.addBar(mapperBar, false);
//                    } catch (IllegalArgumentException e) {
//                        log.error("Illegal argument in {} addBar.\nTime of event close: {}\nTime of barSeries endIndex({}): {}\nTime of mapped bar: {}",
//                                event.getSymbol(),
//                                ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getCloseTime()), ZoneId.systemDefault()), barSeries.getEndIndex(),
//                                barSeries.getBar(barSeries.getEndIndex()).getEndTime(),
//                                mapperBar.getEndTime());
////                        log.info();
//                    }
//                }
//        } else {
//            barSeriesMap.put(event.getSymbol(),
//                    newBaseBarSeries(marketInfo.getCandleSticks(event.getSymbol(), candlestickInterval, baseBarSeriesLimit), event.getSymbol()));
//        }

        Optional.ofNullable(barSeriesMap.get(event.getSymbol())).ifPresentOrElse(barSeries -> {
            if (barSeries.getEndIndex() >= 0) {
                barSeries.addBar(CandlestickToBaseBarMapper.map(event, candlestickInterval),
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getCloseTime()), ZoneId.systemDefault()).equals(barSeries.getBar(barSeries.getEndIndex()).getEndTime())
                );
            }
        }, () -> {
            barSeriesMap.put(event.getSymbol(),
                    newBaseBarSeries(marketInfo.getCandleSticks(event.getSymbol(), candlestickInterval, baseBarSeriesLimit), event.getSymbol()));
        });
    }

    private BaseBarSeries newBaseBarSeries(List<? extends Candle> candles, final String ticker) {
        return barSeriesBuilder
                .withMaxBarCount(baseBarSeriesLimit)
                .withNumTypeOf(DoubleNum::valueOf)
                .withBars(CandlestickToBaseBarMapper.map(candles, marketCandlestickInterval))
                .withName(String.format("%s_%s", ticker, getName()))
                .build();
    }

    private void sellFast(String symbol, float qty, String quoteAsset) {
        if (!marketInfo.pairOrderIsProcessing(symbol, getName())) {
            spotTrading.placeSellOrderFast(symbol, getName(), getId(), qty);
        }
    }

    private void emulateBuy(String symbol, float currentPrice) {
        Optional.ofNullable(emulatedPositions.get(symbol)).ifPresentOrElse(inPosition -> {
            if (!inPosition.get()) {
                inPosition.set(true);
                log.info("BUY {} at {}.", symbol, currentPrice);
                macSawStrategyCondition.addOpenedPosition(symbol, currentPrice, 1F,
                        priceDecreaseFactor, false, getName()
                );
//                Optional.ofNullable(openedPositionsCandleStickEventsStreams.get(symbol)).ifPresentOrElse(stream -> {
//                }, () -> { // do nothing if stream is already running.
//                    openedPositionsCandleStickEventsStreams.put(symbol,
//                            marketInfo.openCandleStickEventsStream(symbol.toLowerCase(), openedPositionsCandlestickInterval,
//                                    openedPositionMonitoringCallback())
//                    );
//                });
            }
        }, () -> {
            emulatedPositions.put(symbol, new AtomicBoolean(true));
            log.info("BUY {} at {}.", symbol, currentPrice);
            macSawStrategyCondition.addOpenedPosition(symbol, currentPrice, 1F,
                    priceDecreaseFactor, false, getName()
            );
//            Optional.ofNullable(openedPositionsCandleStickEventsStreams.get(symbol)).ifPresentOrElse(stream -> {
//            }, () -> { // do nothing if stream is already running.
//                openedPositionsCandleStickEventsStreams.put(symbol,
//                        marketInfo.openCandleStickEventsStream(symbol.toLowerCase(), openedPositionsCandlestickInterval,
//                                openedPositionMonitoringCallback())
//                );
//            });
        });
        Optional.ofNullable(macSawStrategyCondition.getLongPositions().get(symbol)).ifPresent(pos -> {
            pos.updateStamp(LocalDateTime.now());
            pos.threadName(String.format("%s:%s", Thread.currentThread().getName(), Thread.currentThread().getId()));
        });
        Optional.ofNullable(openedPositionsCandleStickEventsStreams.get(symbol)).ifPresentOrElse(stream -> {
        }, () -> { // do nothing if stream is already running.
            openedPositionsCandleStickEventsStreams.put(symbol,
                    marketInfo.openCandleStickEventsStream(symbol.toLowerCase(), openedPositionsCandlestickInterval,
                            openedPositionMonitoringCallback())
            );
        });
    }

    private void emulateSell(String symbol, float price) {
        Optional.ofNullable(emulatedPositions.get(symbol)).ifPresent(inPosition -> {
            if (inPosition.get()) {
                inPosition.set(false);
                log.info("SELL {} at {}, buy price was {}.", symbol, price, Optional.ofNullable(macSawStrategyCondition.getLongPositions().get(symbol)).map(OpenedPosition::avgPrice).orElse(null));
                macSawStrategyCondition.removeOpenedPosition(symbol);
                Optional.ofNullable(openedPositionsCandleStickEventsStreams.remove(symbol)).ifPresentOrElse(stream -> {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                }, () -> {
                    log.warn("Sell event of {} recieved, but have no opened position monitoring stream.", symbol);
                });
                openedPositionsBarSeriesMap.remove(symbol);
            }
        });
    }

    private void closeOpenedWebSocketStreams() {
        Stream.concat(marketCandleStickEventsStreams.entrySet().stream(), openedPositionsCandleStickEventsStreams.entrySet().stream())
                .forEach(entry -> {
                    try {
                        entry.getValue().close();
                        log.debug("WebStream of '{}' closed.", entry.getKey());
                    } catch (IOException e) {
                        log.error(e.getMessage());
                    }
                });
        marketCandleStickEventsStreams.clear();
        openedPositionsCandleStickEventsStreams.clear();
    }

    private void backupOpenedPositions() {
        List<OpenedPosition> savedPositions = dataService.saveAllOpenedPositions(macSawStrategyCondition.getLongPositions().values(), this);
    }

    private void backupSellRecords() {
        dataService.saveAllSellRecords(macSawStrategyCondition.getSellJournal().values(), this);
    }

    private void unSubscribeFromUserUpdateEvents() {
        try {
            botStateService.deleteActiveStrategy(getId().toString());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        unSubscribeFromUserUpdateEvents();
        try {
            closeOpenedWebSocketStreams();
            backupOpenedPositions();
            backupSellRecords();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

}
