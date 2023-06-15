package ru.tyumentsev.cryptopredator.indicatorvirginbot.strategy;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.Candle;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.market.CandlestickInterval;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.DoubleNum;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.backtesting.EmulatorService;
import ru.tyumentsev.cryptopredator.commons.domain.BTCTrend;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.mapping.CandlestickToBaseBarMapper;
import ru.tyumentsev.cryptopredator.commons.service.BotStateService;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;
import ru.tyumentsev.cryptopredator.indicatorvirginbot.cache.IndicatorVirginStrategyCondition;
import ru.tyumentsev.cryptopredator.indicatorvirginbot.configuration.IndicatorVirginConfiguration;

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class IndicatorVirgin implements TradingStrategy {

    IndicatorVirginConfiguration configuration;
    IndicatorVirginStrategyCondition strategyCondition;
    MarketInfo marketInfo;
    SpotTrading spotTrading;
    DataService dataService;
    BotStateService botStateService;
    @NonFinal
    @Getter
    EmulatorService emulatorService;
    Lock lock = new ReentrantLock();
    CandlestickInterval marketCandlestickInterval = CandlestickInterval.HALF_HOURLY;
    CandlestickInterval openedPositionsCandlestickInterval = CandlestickInterval.HALF_HOURLY;
    int baseBarSeriesLimit = 100;
    @Getter
    Map<String, Closeable> marketCandleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    Map<String, Closeable> openedPositionsCandleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    Map<String, BaseBarSeries> marketBarSeriesMap = new ConcurrentHashMap<>();
    @Getter
    Map<String, BaseBarSeries> openedPositionsBarSeriesMap = new ConcurrentHashMap<>();

    BaseBarSeriesBuilder barSeriesBuilder = new BaseBarSeriesBuilder();
    BaseBarSeries emptyBarSeries = new BaseBarSeries("EmptyBarSeries");
    @Getter
    BTCTrend btcTrend = new BTCTrend(CandlestickInterval.DAILY);

    @Scheduled(fixedDelayString = "${strategy.indicatorVirgin.updateBtcTrend.fixedDelay}", initialDelayString = "${strategy.indicatorVirgin.updateBtcTrend.initialDelay}")
    public void indicatorVirgin_updateBTCTrend() {
        if (configuration.indicatorVirginEnabled() && configuration.followBtcTrend()) {
            Optional.ofNullable(dataService.getBTCTrend()).map(BTCTrend::getLastCandles)
                    .ifPresentOrElse(btcTrend::setLastCandles,
                            () -> {
                                log.warn("BTC trend wasn't updated, because state keeper returned no Candlestick. Turning OFF BTC trend following.");
                                configuration.followBtcTrend(false);
                            });
        }
    }

    @Scheduled(fixedDelayString = "${strategy.indicatorVirgin.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.indicatorVirgin.startCandlstickEventsCacheUpdating.initialDelay}")
    public void indicatorVirgin_startCandlstickEventsCacheUpdating() {
        if (configuration.indicatorVirginEnabled() && !configuration.testLaunch()) {
            startCandlstickEventsCacheUpdating();
        }
    }

    @Override
    public String getName() {
        return IndicatorVirginConfiguration.STRATEGY_NAME;
    }

    @Override
    public Integer getId() {
        return IndicatorVirginConfiguration.STRATEGY_ID;
    }

    @Override
    public boolean isEnabled() {
        return configuration.indicatorVirginEnabled();
    }

    @Override
    public void prepareData() {
        if (configuration.testLaunch()) {
            injectEmulatorService();
        } else {
            restoreSellJournalFromCache();
            prepareOpenedLongPositions();
            defineAvailableOrdersLimit();
            subscribeToUserUpdateEvents();
        }
    }

    void injectEmulatorService() {
        strategyCondition.getLongPositions().clear();
        strategyCondition.getShortPositions().clear();
        emulatorService = new EmulatorService(this, strategyCondition);
        log.debug("Emulator service injected into '{}'.", getName());
    }

    private void restoreSellJournalFromCache() {
        var sellJournal = strategyCondition.getSellJournal();
        dataService.findAllSellRecords(this)
                .forEach(record -> sellJournal.put(record.symbol(), record));
        dataService.deleteAllSellRecords(sellJournal.values(), this);
    }

    private void prepareOpenedLongPositions() {
        List<String> accountPositions = spotTrading.recieveOpenedLongPositionsFromMarket().stream()
                .map(assetBalance -> assetBalance.getAsset() + configuration.tradingAsset()).toList();

        List<OpenedPosition> cachedOpenedPositions = dataService.findAllOpenedPositions(this);
        log.debug("Found next cached opened positions: {}", cachedOpenedPositions);
        cachedOpenedPositions.forEach(pos -> {
            if (accountPositions.contains(pos.symbol())) {
                pos.threadStatus(null)
                        .updateStamp(null);
                strategyCondition.getLongPositions().put(pos.symbol(), pos);
            }
        });

        dataService.deleteAllOpenedPositions(strategyCondition.getLongPositions().values(), this);
    }

    private void defineAvailableOrdersLimit() {
        int availableOrdersLimit = configuration.ordersQtyLimit() - strategyCondition.getLongPositions().values().stream()
                .map(openedPosition -> Math.ceil(openedPosition.avgPrice() * openedPosition.qty()))
                .reduce(0D, Double::sum)
                .intValue() / configuration.baseOrderVolume();

        botStateService.setAvailableOrdersLimit(getId(), availableOrdersLimit, configuration.baseOrderVolume());
    }

    private void subscribeToUserUpdateEvents() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("strategyId", getId().toString());
        parameters.put("botAddress", IndicatorVirginConfiguration.USER_DATA_UPDATE_ENDPOINT);
        botStateService.addActiveStrategy(parameters);
    }

    @Override
    public void handleBuying(final OrderTradeUpdateEvent buyEvent) {
        log.debug("Get buy event of {} with strategy id {}", buyEvent.getSymbol(), buyEvent.getStrategyId());
        if (configuration.indicatorVirginEnabled() && getId().equals(buyEvent.getStrategyId())) {
            final String symbol = buyEvent.getSymbol();

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(buyEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(symbol).getPrice())
                    : parsedFloat(buyEvent.getPrice());

            log.info("BUY {} {} at {} (weight: {}). Available orders limit is {}.",
                    buyEvent.getAccumulatedQuantity(), symbol, dealPrice,
                    Optional.ofNullable(strategyCondition.getMonitoredPositions().get(symbol)).map(pos -> String.valueOf(pos.getWeight())).orElse("No monitored position"),
                    botStateService.getAvailableOrdersCount(getId())
            );
//                    buyEvent.getAccumulatedQuantity(), symbol, dealPrice, strategyCondition.getMonitoredPositions().get(symbol).weight(), botStateService.getAvailableOrdersCount(getId()));
            strategyCondition.addOpenedPosition(symbol, dealPrice, parsedFloat(buyEvent.getAccumulatedQuantity()),
                    configuration.priceDecreaseFactor(), false, getName()
            );
            strategyCondition.removePositionFromMonitoring(symbol);

            marketInfo.pairOrderFilled(symbol, getId());

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

        if (configuration.indicatorVirginEnabled() && (getId().equals(Optional.ofNullable(sellEvent.getStrategyId()).orElse(getId())))) {

            Optional.ofNullable(strategyCondition.removeOpenedPosition(symbol)).ifPresent(openedPosition -> {
                // if price == 0 most likely it was market order, use last market price.
                float dealPrice = parsedFloat(sellEvent.getPrice()) == 0
                        ? parsedFloat(marketInfo.getLastTickerPrice(symbol).getPrice())
                        : parsedFloat(sellEvent.getPrice());

                log.info("SELL {} {} at {}. AVG = {} (profit {}%), stopLoss = {}. Available orders limit is {}.",
                        sellEvent.getOriginalQuantity(), symbol, dealPrice, openedPosition.avgPrice(), percentageDifference(dealPrice, openedPosition.avgPrice()), openedPosition.priceDecreaseFactor(), botStateService.getAvailableOrdersCount(getId()));
            });

            strategyCondition.addSellRecordToJournal(symbol, getName());

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

            marketInfo.pairOrderFilled(symbol, getId());
        }
    }

    public void startCandlstickEventsCacheUpdating() {
        closeOpenedWebSocketStreams();
        AtomicInteger marketMonitoringThreadsCounter = new AtomicInteger();
        AtomicInteger openedPositionsMonitoringThreadsCounter = new AtomicInteger();

        Deque<String> marketTickers = marketInfo.getCheapPairs().get(configuration.tradingAsset()).stream().sorted().collect(Collectors.toCollection(LinkedList::new));

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

        strategyCondition.getLongPositions().keySet().forEach(symbol -> {
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

            if (!strategyCondition.getLongPositions().containsKey(event.getSymbol())) {
                analizeMarketPosition(event);
            }
        };
    }

    private BinanceApiCallback<CandlestickEvent> openedPositionMonitoringCallback() {
        return event -> {
            addEventToBaseBarSeries(event, openedPositionsBarSeriesMap, openedPositionsCandlestickInterval);

            if (strategyCondition.getLongPositions().containsKey(event.getSymbol())) {
                analizeOpenedPosition(event, strategyCondition.getLongPositions().get(event.getSymbol()));
            }
        };
    }

    private void analizeMarketPosition(final CandlestickEvent event) {
        if (strategyCondition.pong(event.getSymbol())) {
            log.info("Pong from market monitoring event for pair {}:\nisAlive:{}/state:{}.\n{}", event.getSymbol(), Thread.currentThread().isAlive(), Thread.currentThread().getState(), event);
        }
        if (marketBarSeriesMap.get(event.getSymbol()).getBarCount() < baseBarSeriesLimit - 1) {
            return;
        }
        if (strategyCondition.pairOnMonitoring(event.getSymbol(), marketBarSeriesMap.getOrDefault(event.getSymbol(), emptyBarSeries))) {
//        if (strategyCondition.pairOnMonitoring(event.getSymbol(), Optional.ofNullable(marketBarSeriesMap.get(event.getSymbol())).orElseGet(BaseBarSeries::new))) {
            analizeMonitoredPosition(event);
//            buyFast(event.getSymbol(), parsedFloat(event.getClose()), tradingAsset, false);
        } else if (signalToOpenLongPosition(event)) {
            strategyCondition.addPairToMonitoring(event.getSymbol(), parsedFloat(event.getClose()));
        }
    }

    private boolean signalToOpenLongPosition(final CandlestickEvent event) {
//        if (Optional.ofNullable(emulatedPositions.get(event.getSymbol())).map(AtomicBoolean::get).orElse(false)) {
//            return false;
//        }
        if ((marketInfo.pairOrderIsProcessing(event.getSymbol(), getId()) || strategyCondition.thisSignalWorkedOutBefore(event.getSymbol()))
                || (configuration.followBtcTrend() && btcTrend.isBearish())) {
            return false;
        }

        BaseBarSeries series = marketBarSeriesMap.getOrDefault(event.getSymbol(), emptyBarSeries);
//        BaseBarSeries series = Optional.ofNullable(marketBarSeriesMap.get(event.getSymbol())).orElseGet(BaseBarSeries::new);
        if (series.getBarData().isEmpty()) {
            return false;
        }
        var endBarSeriesIndex = series.getEndIndex();

//        SMAIndicator sma200 = new SMAIndicator(new ClosePriceIndicator(series), 200);
        EMAIndicator ema7 = new EMAIndicator(new ClosePriceIndicator(series), 7);
        EMAIndicator ema25 = new EMAIndicator(new ClosePriceIndicator(series), 25);
//        RSIIndicator rsi14 = new RSIIndicator(new ClosePriceIndicator(series), 14);
//        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), 12, 26);

        var ema7Value = ema7.getValue(endBarSeriesIndex);
        var ema25Value = ema25.getValue(endBarSeriesIndex);
//        var rsi14Value = rsi14.getValue(endBarSeriesIndex);

        if (//ema25.getValue(series.getEndIndex() - 1).isGreaterThanOrEqual(sma200.getValue(endBarSeriesIndex)) &&
                ema7Value.isGreaterThan(ema25Value) &&
//                rsi14.getValue(endBarSeriesIndex).isGreaterThan(DoubleNum.valueOf(73)) &&
                (itsSustainableGrowth(ema7, ema25, endBarSeriesIndex, 2) &&
                        haveBreakdown(ema7, ema25, endBarSeriesIndex, 8) //&&
//                        rsi14.getValue(endBarSeriesIndex - 1).isGreaterThan(DoubleNum.valueOf(72))
                    )
            ) {
//                && sma7Value.isLessThanOrEqual(sma25Value.multipliedBy(DoubleNum.valueOf(1.06F)))
//            log.debug("SMA7 of {} ({}) is higher then SMA25 ({}) with RSI14 ({}) is greater then 72.", event.getSymbol(), ema7Value, ema25Value, rsi14Value);
            return true;
        }

        return false;
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

    private void analizeMonitoredPosition(final CandlestickEvent event) {
        BaseBarSeries series = marketBarSeriesMap.getOrDefault(event.getSymbol(), emptyBarSeries);
//        BaseBarSeries series = Optional.ofNullable(marketBarSeriesMap.get(event.getSymbol())).orElseGet(BaseBarSeries::new);
        if (series.getBarData().isEmpty() || !strategyHaveAvailableOrdersLimit()) {
            return;
        }

        final String symbol = event.getSymbol();
        strategyCondition.getMonitoredPositionPrice(symbol).ifPresent(startPrice -> {
            float currentPrice = parsedFloat(event.getClose());
            var endBarSeriesIndex = series.getEndIndex();

            // set weight
            strategyCondition.setMonitoredPairWeight(symbol, (int) percentageDifference(currentPrice, startPrice));

//            RSIIndicator rsi14 = new RSIIndicator(new ClosePriceIndicator(series), 14);
//            var rsi14Value = rsi14.getValue(endBarSeriesIndex - 1);

            var currentTime = ZonedDateTime.now(ZoneId.systemDefault());
            if (series.getBar(endBarSeriesIndex).getEndTime().isBefore(currentTime)
                    || currentTime.isBefore(series.getBar(endBarSeriesIndex).getBeginTime().plusMinutes(5))) {
                return;
            }

            if (series.getBar(endBarSeriesIndex - 1).getClosePrice()
                    .isGreaterThan(DoubleNum.valueOf(startPrice).multipliedBy(DoubleNum.valueOf(1.03)))
                //                    && rsi14Value.isGreaterThanOrEqual(DoubleNum.valueOf(70))
                    && series.getBar(endBarSeriesIndex).getClosePrice().isGreaterThan(series.getBar(endBarSeriesIndex - 1).getHighPrice())
                    && strategyCondition.itsHeaviestMonitoredPair(symbol)
                    && strategyCondition.pairOnUptrend(symbol, currentPrice, CandlestickInterval.DAILY, marketInfo)
            ) {
                buyFast(symbol, currentPrice, configuration.tradingAsset(), false);
            }
        });
    }

    private boolean strategyHaveAvailableOrdersLimit() {
        lock.lock();
        try {
            return botStateService.getAvailableOrdersCount(getId()) > 0;
        } finally {
            lock.unlock();
        }
    }

    private void analizeOpenedPosition(final CandlestickEvent event, OpenedPosition openedPosition) {
        final String symbol = event.getSymbol();
        var currentPrice = parsedFloat(event.getClose());

        if (strategyCondition.pong(symbol)) {
            log.info("Pong from opened position monitoring event for pair {}:\nisAlive:{}/state:{}.\n{}", symbol, Thread.currentThread().isAlive(), Thread.currentThread().getState(), event);
        }

        openedPosition.updateLastPrice(currentPrice);
//        strategyCondition.updateOpenedPositionLastPrice(symbol, currentPrice, strategyCondition.getLongPositions());

        if (currentPrice > openedPosition.avgPrice() * configuration.pairTakeProfitFactor() && !openedPosition.priceDecreaseFactor().equals(configuration.takeProfitPriceDecreaseFactor())) {
            openedPosition.priceDecreaseFactor(configuration.takeProfitPriceDecreaseFactor());
        }

        if (signalToCloseLongPosition(event, openedPosition)) {
            emulatorService.emulateSell(event.getSymbol(), currentPrice);
//            sellFast(event.getSymbol(), openedPosition.qty(), configuration.tradingAsset());
        } else if (needToAverage(openedPosition)) {
//        } else if (averagingEnabled && currentPrice > openedPosition.avgPrice() * averagingTrigger) {
            openedPosition.rocketCandidate(true);
            buyFast(symbol, currentPrice, configuration.tradingAsset(), true);
        }

    }

    private boolean needToAverage(final OpenedPosition openedPosition) {
        if (!configuration.averagingEnabled()) {
            return false;
        }

//        if (openedPosition.lastPrice() > openedPosition.avgPrice() * averagingTrigger) {
//            BaseBarSeries series = openedPositionsBarSeriesMap.getOrDefault(openedPosition.symbol(), emptyBarSeries);
//            if (series.getBarData().isEmpty()) {
//                return false;
//            }
        float futureAvgPrice = openedPosition.calculateFutureAvgPrice(configuration.baseOrderVolume(), OrderSide.BUY);
        return openedPosition.lastPrice() > openedPosition.stopPrice()
                && futureAvgPrice < openedPosition.stopPrice() * configuration.averagingTrigger()
                && futureAvgPrice < openedPosition.lastPrice() * configuration.averagingTrigger();
//            EMAIndicator ema25 = new EMAIndicator(new HighPriceIndicator(series), 25);
//            return ema25.getValue(series.getEndIndex()).isGreaterThan(DoubleNum.valueOf(openedPosition.calculateFutureAvgPrice(baseOrderVolume)));
//        } else {
//            return false;
//        }
    }

    private void buyFast(final String symbol, final float price, String quoteAsset, boolean itsAveraging) {
        if (!(marketInfo.pairOrderIsProcessing(symbol, getId()) || strategyCondition.thisSignalWorkedOutBefore(symbol))) {
            emulatorService.emulateBuy(symbol, price);
//            spotTrading.placeBuyOrderFast(symbol, getId(), price, quoteAsset, configuration.minimalAssetBalance(), configuration.baseOrderVolume());
        }
    }

    private boolean signalToCloseLongPosition(final CandlestickEvent event, OpenedPosition openedPosition) {
        if (marketInfo.pairOrderIsProcessing(event.getSymbol(), getId())) {
            return false;
        }
        final String ticker = event.getSymbol();

        BaseBarSeries series = openedPositionsBarSeriesMap.getOrDefault(event.getSymbol(), emptyBarSeries);
//        BaseBarSeries series = Optional.ofNullable(openedPositionsBarSeriesMap.get(event.getSymbol())).orElseGet(BaseBarSeries::new);
        if (series.getBarData().isEmpty()) {
            log.warn("Opened positions BaseBarSeries of {} is empty, cannot define signal to close opened position.", ticker);
            return false;
        }
        var endBarSeriesIndex = series.getEndIndex();
        EMAIndicator ema7 = new EMAIndicator(new ClosePriceIndicator(series), 7);
        EMAIndicator ema25 = new EMAIndicator(new HighPriceIndicator(series), 25);

        openedPosition.stopPrice(ema25.getValue(endBarSeriesIndex - 1).floatValue());

//        RSIIndicator rsi14 = new RSIIndicator(new ClosePriceIndicator(series), 14);
//        MACDIndicator macdIndicator = new MACDIndicator(new ClosePriceIndicator(series), 12, 26);
//
//        float macd9barsAVG = macdSignalLineValue(macdIndicator, endBarSeriesIndex, 9);

//        var currentPrice = parsedFloat(event.getClose());
//        float stopTriggerValue = openedPosition.priceDecreaseFactor().equals(takeProfitPriceDecreaseFactor) ? openedPosition.maxPrice() : openedPosition.avgPrice();
//        float stopTriggerValue = openedPosition.maxPrice();

        if (LocalDateTime.now(ZoneId.systemDefault()).minusDays(2L).isAfter(openedPosition.lastDealTime()) &&
                ema7.getValue(endBarSeriesIndex - 1).isLessThan(ema25.getValue(endBarSeriesIndex - 1))
                //currentPrice < stopTriggerValue * openedPosition.priceDecreaseFactor() &&
//                (series.getBar(endBarSeriesIndex - 1).isBearish() &&
//                rsi14.getValue(endBarSeriesIndex).isLessThanOrEqual(DoubleNum.valueOf(67)) &&
//                macdIndicator.getValue(endBarSeriesIndex).isLessThan(DoubleNum.valueOf(macd9barsAVG))) // current MACD less or equals signal line.
//                || currentPrice > openedPosition.avgPrice() * 1.05
//                && (macdIndicator.getValue(endBarSeriesIndex).isLessThanOrEqual(macdIndicator.getValue(endBarSeriesIndex - 1))
////                    || series.getBar(endBarSeriesIndex).getClosePrice().isGreaterThan(series.getBar(endBarSeriesIndex).getOpenPrice().multipliedBy(DoubleNum.valueOf(1.1)))
//                    || currentPrice > openedPosition.avgPrice() * 1.1)
        ) {
//            log.debug("PRICE of {} DECREASED and now equals '{}' (rsi14 is '{}', current MACD is '{}', signal MACD line is '{}'), price decrease factor is {} / {}.",
//                    ticker, currentPrice, rsi14.getValue(endBarSeriesIndex), macdIndicator.getValue(endBarSeriesIndex), macd9barsAVG,
//                    openedPosition.priceDecreaseFactor(), indicatorVirginStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());
            return true;
        }
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
        var series = barSeriesMap.getOrDefault(event.getSymbol(), newBaseBarSeries(marketInfo.getCandleSticks(event.getSymbol(), candlestickInterval, baseBarSeriesLimit), event.getSymbol(), candlestickInterval));
        boolean replace = false;
        if (series.getBarCount() > 0) {
            replace = ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getCloseTime()), ZoneId.systemDefault()).isEqual(series.getLastBar().getEndTime());
        }
        series.addBar(CandlestickToBaseBarMapper.map(event, candlestickInterval),replace);
        marketBarSeriesMap.put(event.getSymbol(), series);

//        Optional.ofNullable(barSeriesMap.get(event.getSymbol())).ifPresentOrElse(barSeries -> {
//            if (barSeries.getEndIndex() >= 0) {
//                barSeries.addBar(CandlestickToBaseBarMapper.map(event, candlestickInterval),
//                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getCloseTime()), ZoneId.systemDefault()).isEqual(barSeries.getLastBar().getEndTime())
//                );
//            }
//        }, () -> {
//            barSeriesMap.put(event.getSymbol(),
//                    newBaseBarSeries(marketInfo.getCandleSticks(event.getSymbol(), candlestickInterval, baseBarSeriesLimit), event.getSymbol(), candlestickInterval));
//        });
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
        if (!marketInfo.pairOrderIsProcessing(symbol, getId())) {
            spotTrading.placeSellOrderFast(symbol, getId(), qty);
        }
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
        List<OpenedPosition> savedPositions = dataService.saveAllOpenedPositions(strategyCondition.getLongPositions().values(), this);
    }

    private void backupSellRecords() {
        dataService.saveAllSellRecords(strategyCondition.getSellJournal().values(), this);
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
        if (!configuration.indicatorVirginEnabled()) {
            return;
        }

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
