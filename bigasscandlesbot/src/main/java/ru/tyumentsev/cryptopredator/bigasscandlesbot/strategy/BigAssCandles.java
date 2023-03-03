package ru.tyumentsev.cryptopredator.bigasscandlesbot.strategy;

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
import org.springframework.stereotype.Service;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNum;
import ru.tyumentsev.cryptopredator.bigasscandlesbot.cache.BigAssCandlesStrategyCondition;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.domain.BTCTrend;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.mapping.CandlestickToBaseBarMapper;
import ru.tyumentsev.cryptopredator.commons.service.BotStateService;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;

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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
@Slf4j
public class BigAssCandles implements TradingStrategy {

    final MarketInfo marketInfo;
    final BigAssCandlesStrategyCondition bigAssCandlesStrategyCondition;
    final SpotTrading spotTrading;
    final DataService dataService;
    final BotStateService botStateService;

    final CandlestickInterval marketCandlestickInterval = CandlestickInterval.FIFTEEN_MINUTES;
    final CandlestickInterval openedPositionsCandlestickInterval = CandlestickInterval.FIFTEEN_MINUTES;
    final int baseBarSeriesLimit = 200;
    @Getter
    final Map<String, Closeable> marketCandleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    final Map<String, Closeable> openedPositionsCandleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    final Map<String, BaseBarSeries> marketBarSeriesMap = new ConcurrentHashMap<>();
    @Getter
    final Map<String, BaseBarSeries> openedPositionsBarSeriesMap = new ConcurrentHashMap<>();

    final BaseBarSeriesBuilder barSeriesBuilder = new BaseBarSeriesBuilder();
    @Getter
    final BTCTrend btcTrend = new BTCTrend(CandlestickInterval.DAILY);
    final static String STRATEGY_NAME = "bigasscandles";
    final static Integer STRATEGY_ID = 1004;
    final static String USER_DATA_UPDATE_ENDPOINT = "http://bigasscandlesbot:8080/state/userDataUpdateEvent";

    final Map<String, AtomicBoolean> emulatedPositions = new ConcurrentHashMap<>();

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.bigAssCandles.enabled}")
    boolean bigAssCandlesEnabled;
    @Value("${strategy.bigAssCandles.followBtcTrend}")
    boolean followBtcTrend;
    @Value("${strategy.bigAssCandles.ordersQtyLimit}")
    int ordersQtyLimit;
    @Value("${strategy.bigAssCandles.averagingEnabled}")
    boolean averagingEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.minimalAssetBalance}")
    int minimalAssetBalance;
    @Value("${strategy.global.baseOrderVolume}")
    int baseOrderVolume;
    @Value("${strategy.bigAssCandles.priceDecreaseFactor}")
    float priceDecreaseFactor;
    @Value("${strategy.bigAssCandles.pairTakeProfitFactor}")
    float pairTakeProfitFactor;
    @Value("${strategy.bigAssCandles.takeProfitPriceDecreaseFactor}")
    float takeProfitPriceDecreaseFactor;
    @Value("${strategy.bigAssCandles.averagingTrigger}")
    float averagingTrigger;

//    @Scheduled(fixedDelayString = "${strategy.bigAssCandles.updateBtcTrend.fixedDelay}", initialDelayString = "${strategy.bigAssCandles.updateBtcTrend.initialDelay}")
//    public void bigAssCandles_updateBTCTrend() {
//        if (bigAssCandlesEnabled && followBtcTrend) {
//            Optional.ofNullable(dataService.getBTCTrend()).map(BTCTrend::getLastCandle)
//                    .ifPresentOrElse(btcTrend::setLastCandle,
//                            () -> log.warn("BTC trend wasn't updated, because state keeper returned no Candlestick."));
//        }
//    }

    @Scheduled(fixedDelayString = "${strategy.bigAssCandles.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.bigAssCandles.startCandlstickEventsCacheUpdating.initialDelay}")
    public void bigAssCandles_startCandlstickEventsCacheUpdating() {
        if (bigAssCandlesEnabled && !testLaunch) {
            startCandlstickEventsCacheUpdating();
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
        return bigAssCandlesEnabled;
    }

    @Override
    public void prepareData() {
        restoreSellJournalFromCache();
        prepareOpenedLongPositions();
        defineAvailableOrdersLimit();
        subscribeToUserUpdateEvents();
    }

    private void restoreSellJournalFromCache() {
        var sellJournal = bigAssCandlesStrategyCondition.getSellJournal();
        dataService.findAllSellRecords(this)
                .forEach(record -> sellJournal.put(record.symbol(), record));
        dataService.deleteAllSellRecords(sellJournal.values(), this);
    }

    private void prepareOpenedLongPositions() {
        List<String> accountPositions = spotTrading.recieveOpenedLongPositionsFromMarket().stream()
                .map(assetBalance -> assetBalance.getAsset() + tradingAsset).toList();

        List<OpenedPosition> cachedOpenedPositions = dataService.findAllOpenedPositions(this);
        log.debug("Found next cached opened positions: {}", cachedOpenedPositions);
        cachedOpenedPositions.forEach(pos -> {
            if (accountPositions.contains(pos.symbol())) {
                pos.threadName(null)
                        .updateStamp(null);
                bigAssCandlesStrategyCondition.getLongPositions().put(pos.symbol(), pos);
            }
        });

        dataService.deleteAllOpenedPositions(bigAssCandlesStrategyCondition.getLongPositions().values(), this);
    }

    private void defineAvailableOrdersLimit() {
        int availableOrdersLimit = ordersQtyLimit - bigAssCandlesStrategyCondition.getLongPositions().values().stream()
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
        if (bigAssCandlesEnabled && getId().equals(buyEvent.getStrategyId())) {
            final String symbol = buyEvent.getSymbol();

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(buyEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(symbol).getPrice())
                    : parsedFloat(buyEvent.getPrice());

            log.info("BUY {} {} at {}. Available orders limit is {}.",
                    buyEvent.getAccumulatedQuantity(), symbol, dealPrice, botStateService.getAvailableOrdersCount(getId()));
            bigAssCandlesStrategyCondition.addOpenedPosition(symbol, dealPrice, parsedFloat(buyEvent.getAccumulatedQuantity()),
                    priceDecreaseFactor, false, getName()
            );
            bigAssCandlesStrategyCondition.removePositionFromMonitoring(symbol);

            marketInfo.pairOrderFilled(symbol, getName());

            Optional.ofNullable(openedPositionsCandleStickEventsStreams.get(symbol)).ifPresentOrElse(stream -> {
            }, () -> { // do nothing if stream is already running.
                openedPositionsCandleStickEventsStreams.put(symbol,
                        marketInfo.openCandleStickEventsStream(symbol.toLowerCase(), openedPositionsCandlestickInterval,
                                openedPositionMonitoringCallback())
                );
            });
        }
    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent sellEvent) {
        final String symbol = sellEvent.getSymbol();
        log.debug("Get sell event of {} with strategy id {}.", symbol, sellEvent.getStrategyId());

        if (bigAssCandlesEnabled && (getId().equals(Optional.ofNullable(sellEvent.getStrategyId()).orElse(getId())))) {

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(sellEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(symbol).getPrice())
                    : parsedFloat(sellEvent.getPrice());

            var openedPosition = bigAssCandlesStrategyCondition.removeOpenedPosition(symbol);;
            log.info("SELL {} {} at {}. AVG = {} (profit {}%), stopLoss = {}. Available orders limit is {}.",
                    sellEvent.getOriginalQuantity(), symbol, dealPrice, openedPosition.avgPrice(), percentageDifference(dealPrice, openedPosition.avgPrice()), openedPosition.priceDecreaseFactor(), botStateService.getAvailableOrdersCount(getId()));

            bigAssCandlesStrategyCondition.addSellRecordToJournal(symbol, getName());

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

        bigAssCandlesStrategyCondition.getLongPositions().keySet().forEach(symbol -> {
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

            Optional.ofNullable(bigAssCandlesStrategyCondition.getLongPositions().get(event.getSymbol())).ifPresentOrElse(
                    openedPosition -> {},//analizeOpenedPosition(event, openedPosition), // ignore opened positions
                    () -> analizeMarketPosition(event));
        };
    }

    private BinanceApiCallback<CandlestickEvent> openedPositionMonitoringCallback() {
        return event -> {
            addEventToBaseBarSeries(event, openedPositionsBarSeriesMap, openedPositionsCandlestickInterval);

            Optional.ofNullable(bigAssCandlesStrategyCondition.getLongPositions().get(event.getSymbol())).ifPresent(
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
//        if (marketInfo.pairOrderIsProcessing(event.getSymbol(), getName()) || bigAssCandlesStrategyCondition.thisSignalWorkedOutBefore(event.getSymbol())) {
//            return false;
//        }

        BaseBarSeries series = Optional.ofNullable(marketBarSeriesMap.get(event.getSymbol())).orElseGet(BaseBarSeries::new);
        if (series.getBarData().isEmpty()) {
            return false;
        }
        var endBarSeriesIndex = series.getEndIndex();

        SMAIndicator sma200 = new SMAIndicator(new ClosePriceIndicator(series), 200);
        RSIIndicator rsi14 = new RSIIndicator(new ClosePriceIndicator(series), 14);

//        EMAIndicator ema7 = new EMAIndicator(new ClosePriceIndicator(series), 7);
        EMAIndicator ema25 = new EMAIndicator(new ClosePriceIndicator(series), 25);
//        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), 12, 26);

//        var ema7Value = ema7.getValue(endBarSeriesIndex);
//        var ema25Value = ema25.getValue(endBarSeriesIndex);
        var currentPrice = DoubleNum.valueOf(parsedFloat(event.getClose()));
        var previousBar = series.getBar(series.getEndIndex() - 1);
        var sma200Value = sma200.getValue(endBarSeriesIndex);
        var rsi14Value = rsi14.getValue(endBarSeriesIndex);

        if (rsi14Value.isGreaterThanOrEqual(DoubleNum.valueOf(55)) &&
                sma200Value.isLessThan(currentPrice) &&
                previousBar.isBullish() &&
                previousBar.getClosePrice().isGreaterThan(ema25.getValue(series.getEndIndex() - 2)) &&
//                previousBar.getClosePrice().isLessThan(currentPrice.multipliedBy(DoubleNum.valueOf(1.01))) &&
                allBarsAreBearish(series, series.getEndIndex() - 2, 3) &&
                previousBar.getClosePrice().isGreaterThan(series.getBar(series.getEndIndex() - 2).getOpenPrice())
            ) {
            return true;
        }

        return false;
    }

    private boolean allBarsAreBearish(final BaseBarSeries series, final int lastIndex, final int barsQty) {
        for (int i = lastIndex; i > lastIndex - barsQty; i--) {
            if (series.getBar(i).getClosePrice().isGreaterThanOrEqual(series.getBar(i).getOpenPrice())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Growth is sustainable if short MA of last N bars are higher or equal than their long MA.
     * @param ema7 short MA
     * @param ema25 long MA
     * @param endBarSeriesIndex last bar index
     * @param barsQty quantity of bars to analize
     * @return True if growth is sustainable.
     */
    private boolean itsSustainableGrowth(final EMAIndicator ema7, final EMAIndicator ema25, final int endBarSeriesIndex, final int barsQty) {
        for (int i = endBarSeriesIndex; i > endBarSeriesIndex - barsQty; i--) {
            if (ema25.getValue(i).isGreaterThan(ema7.getValue(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean haveBreakdown(final EMAIndicator ema7, final EMAIndicator ema25, final int endBarSeriesIndex, final int barsQty) {
        for (int i = endBarSeriesIndex; i > endBarSeriesIndex - barsQty; i--) {
            if (ema25.getValue(i).isGreaterThan(ema7.getValue(i))) {
                return true;
            }
        }
        return false;
    }

    private void analizeOpenedPosition(final CandlestickEvent event, final OpenedPosition openedPosition) {
        final String symbol = event.getSymbol();
        var currentPrice = parsedFloat(event.getClose());

        bigAssCandlesStrategyCondition.updateOpenedPositionLastPrice(symbol, currentPrice, bigAssCandlesStrategyCondition.getLongPositions());
        bigAssCandlesStrategyCondition.updateOpenedPositionStopPrice(openedPosition,
                Optional.ofNullable(openedPositionsBarSeriesMap.get(symbol)).orElseGet(BaseBarSeries::new)
        );

        if (signalToCloseLongPosition(event, openedPosition)) {
            emulateSell(event.getSymbol(), currentPrice);
//            sellFast(event.getSymbol(), openedPosition.qty(), tradingAsset);
        }
    }

    private void buyFast(final String symbol, final float price, String quoteAsset, boolean itsAveraging) {
        if (!(marketInfo.pairOrderIsProcessing(symbol, getName()) || bigAssCandlesStrategyCondition.thisSignalWorkedOutBefore(symbol))) {
//            emulateBuy(symbol, price);
            spotTrading.placeBuyOrderFast(symbol, getName(), getId(), price, quoteAsset, minimalAssetBalance, baseOrderVolume);
        }
    }

    private void emulateBuy(String symbol, float currentPrice) {
        Optional.ofNullable(emulatedPositions.get(symbol)).ifPresentOrElse(inPosition -> {
            if (!inPosition.get()) {
                inPosition.set(true);
                log.info("BUY {} at {}.", symbol, currentPrice);
                bigAssCandlesStrategyCondition.addOpenedPosition(symbol, currentPrice, 1F,
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
            bigAssCandlesStrategyCondition.addOpenedPosition(symbol, currentPrice, 1F,
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
        Optional.ofNullable(bigAssCandlesStrategyCondition.getLongPositions().get(symbol)).ifPresent(pos -> {
            pos.updateStamp(LocalDateTime.now());
            pos.threadName(String.format("%s:%s", Thread.currentThread().getName(), Thread.currentThread().getId()));

            bigAssCandlesStrategyCondition.updateOpenedPositionStopPrice(pos, Optional.ofNullable(marketBarSeriesMap.get(pos.symbol())).orElseGet(BaseBarSeries::new));
        });
        Optional.ofNullable(openedPositionsCandleStickEventsStreams.get(symbol)).ifPresentOrElse(stream -> {
        }, () -> { // do nothing if stream is already running.
            openedPositionsCandleStickEventsStreams.put(symbol,
                    marketInfo.openCandleStickEventsStream(symbol.toLowerCase(), openedPositionsCandlestickInterval,
                            openedPositionMonitoringCallback())
            );
        });
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
        final String ticker = event.getSymbol();

        BaseBarSeries series = Optional.ofNullable(openedPositionsBarSeriesMap.get(event.getSymbol())).orElseGet(BaseBarSeries::new);
        if (series.getBarData().isEmpty()) {
            log.warn("Opened positions BaseBarSeries of {} is empty, cannot define signal to close opened position.", ticker);
            return false;
        }
        var endBarSeriesIndex = series.getEndIndex();

        RSIIndicator rsi14 = new RSIIndicator(new ClosePriceIndicator(series), 14);
        MACDIndicator macdIndicator = new MACDIndicator(new ClosePriceIndicator(series), 10, 22);

//        float macd9barsAVG = macdSignalLineValue(macdIndicator, endBarSeriesIndex, 9);

//        var currentPrice = parsedFloat(event.getClose());
//        float stopTriggerValue = openedPosition.priceDecreaseFactor().equals(takeProfitPriceDecreaseFactor) ? openedPosition.maxPrice() : openedPosition.avgPrice();
//        float stopTriggerValue = openedPosition.maxPrice();

        if (series.getBar(endBarSeriesIndex - 1).getClosePrice().floatValue() < openedPosition.stopPrice()) {
            return true;
        }

//        if (currentPrice < stopTriggerValue * openedPosition.priceDecreaseFactor() &&
//                series.getBar(endBarSeriesIndex - 1).isBearish() &&
////                rsi14.getValue(endBarSeriesIndex).isLessThanOrEqual(DoubleNum.valueOf(67)) &&
//                macdIndicator.getValue(endBarSeriesIndex).isLessThan(DoubleNum.valueOf(macd9barsAVG)) // current MACD less or equals signal line.
////                && (macdIndicator.getValue(endBarSeriesIndex).isLessThanOrEqual(macdIndicator.getValue(endBarSeriesIndex - 1))
//////                    || series.getBar(endBarSeriesIndex).getClosePrice().isGreaterThan(series.getBar(endBarSeriesIndex).getOpenPrice().multipliedBy(DoubleNum.valueOf(1.1)))
////                    || currentPrice > openedPosition.avgPrice() * 1.1)
//        ) {
//            log.debug("PRICE of {} DECREASED and now equals '{}' (rsi14 is '{}', current MACD is '{}', signal MACD line is '{}'), price decrease factor is {} / {}.",
//                    ticker, currentPrice, rsi14.getValue(endBarSeriesIndex), macdIndicator.getValue(endBarSeriesIndex), macd9barsAVG,
//                    openedPosition.priceDecreaseFactor(), bigAssCandlesStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());
//            return true;
//        }
        return false;
    }

    private float macdSignalLineValue(final MACDIndicator macdIndicator, final int endBarSeriesIndex, final int signalLineLehgth) {
        float macdAVG = 0F;
        for (int i = endBarSeriesIndex; i > endBarSeriesIndex - signalLineLehgth; i--) {
            macdAVG += macdIndicator.getValue(i).floatValue();
        }
        return  macdAVG / signalLineLehgth;
    }

    public void addEventToBaseBarSeries(final CandlestickEvent event, final Map<String, BaseBarSeries> barSeriesMap, final CandlestickInterval candlestickInterval) {
        Optional.ofNullable(barSeriesMap.get(event.getSymbol())).ifPresentOrElse(barSeries -> {
            if (barSeries.getEndIndex() >= 0) {
                barSeries.addBar(CandlestickToBaseBarMapper.map(event, candlestickInterval),
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getCloseTime()), ZoneId.systemDefault()).equals(barSeries.getBar(barSeries.getEndIndex()).getEndTime())
                );
            }
        }, () -> {
            barSeriesMap.put(event.getSymbol(),
                    newBaseBarSeries(marketInfo.getCandleSticks(event.getSymbol(), candlestickInterval, baseBarSeriesLimit), event.getSymbol(), candlestickInterval));
        });
    }

    private BaseBarSeries newBaseBarSeries(final List<? extends Candle> candles, final String ticker, final CandlestickInterval candlestickInterval) {
        return barSeriesBuilder
                .withMaxBarCount(baseBarSeriesLimit)
                .withNumTypeOf(DoubleNum::valueOf)
                .withBars(CandlestickToBaseBarMapper.map(candles, candlestickInterval))
                .withName(String.format("%s_%s", ticker, getName()))
                .build();
    }

    private void sellFast(String symbol, float qty, String quoteAsset) {
        if (!marketInfo.pairOrderIsProcessing(symbol, getName())) {
            spotTrading.placeSellOrderFast(symbol, getName(), getId(), qty);
        }
    }

    private void emulateSell(String symbol, float price) {
        Optional.ofNullable(emulatedPositions.get(symbol)).ifPresent(inPosition -> {
            if (inPosition.get()) {
                inPosition.set(false);
                log.info("SELL {} at {}, buy price was {} (profit {}%).",
                        symbol,
                        price,
                        Optional.ofNullable(bigAssCandlesStrategyCondition.getLongPositions().get(symbol)).map(OpenedPosition::avgPrice).orElse(null),
                        percentageDifference(price, Optional.ofNullable(bigAssCandlesStrategyCondition.getLongPositions().get(symbol)).map(OpenedPosition::avgPrice).orElse(0F))
                );
                bigAssCandlesStrategyCondition.removeOpenedPosition(symbol);
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
        List<OpenedPosition> savedPositions = dataService.saveAllOpenedPositions(bigAssCandlesStrategyCondition.getLongPositions().values(), this);
    }

    private void backupSellRecords() {
        dataService.saveAllSellRecords(bigAssCandlesStrategyCondition.getSellJournal().values(), this);
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
//            backupOpenedPositions();
            backupSellRecords();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
