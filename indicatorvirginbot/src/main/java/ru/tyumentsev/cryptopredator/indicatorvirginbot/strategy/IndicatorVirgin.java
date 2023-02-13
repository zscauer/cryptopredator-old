package ru.tyumentsev.cryptopredator.indicatorvirginbot.strategy;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.Candle;
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
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNum;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.mapping.CandlestickToBaseBarMapper;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;
import ru.tyumentsev.cryptopredator.indicatorvirginbot.cache.IndicatorVirginStrategyCondition;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class IndicatorVirgin implements TradingStrategy {

    final MarketInfo marketInfo;
    final IndicatorVirginStrategyCondition indicatorVirginStrategyCondition;
    final SpotTrading spotTrading;
    final DataService dataService;

    final CandlestickInterval candlestickInterval = CandlestickInterval.HALF_HOURLY;
    final int baseBarSeriesLimit = 26;
    @Getter
    final Map<String, Closeable> candleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    final Map<String, BaseBarSeries> barSeriesMap = new ConcurrentHashMap<>();

    final BaseBarSeriesBuilder barSeriesBuilder = new BaseBarSeriesBuilder()
            .withMaxBarCount(baseBarSeriesLimit)
            .withNumTypeOf(DoubleNum::valueOf);
    final static String STRATEGY_NAME = "indicatorvirgin";
    final static Integer STRATEGY_ID = 1002;
    final static String USER_DATA_UPDATE_ENDPOINT = "http://indicatorvirginbot:8080/state/userDataUpdateEvent";

    final Map<String, AtomicBoolean> emulatedPositions = new ConcurrentHashMap<>();

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.indicatorVirgin.enabled}")
    boolean indicatorVirginEnabled;
    @Value("${strategy.indicatorVirgin.averagingEnabled}")
    boolean averagingEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.minimalAssetBalance}")
    int minimalAssetBalance;
    @Value("${strategy.global.baseOrderVolume}")
    int baseOrderVolume;
    @Value("${strategy.indicatorVirgin.priceDecreaseFactor}")
    float priceDecreaseFactor;
    @Value("${strategy.indicatorVirgin.pairTakeProfitFactor}")
    float pairTakeProfitFactor;
    @Value("${strategy.indicatorVirgin.takeProfitPriceDecreaseFactor}")
    float takeProfitPriceDecreaseFactor;


    @Scheduled(fixedDelayString = "${strategy.indicatorVirgin.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.indicatorVirgin.startCandlstickEventsCacheUpdating.initialDelay}")
    public void indicatorVirgin_startCandlstickEventsCacheUpdating() {
        if (indicatorVirginEnabled) { //&& !testLaunch) {
            startCandlstickEventsCacheUpdating();
//            Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).forEach(thread -> {
//                log.info("Thread {} name: {} group: {}.", thread.getId(), thread.getName(), thread.getThreadGroup());
//            });
        }
    }

//    @Scheduled(fixedDelayString = "240000", initialDelayString = "300000")
//    public void indicatorVirgin_checkThredsState() {
//        if (testLaunch) {
//            Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).forEach(thread -> {
//                log.info("Thread {} name: {} group: {} is alive.", thread.getId(), thread.getName(), thread.getThreadGroup());
//            });
//        }
//    }

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
        return indicatorVirginEnabled;
    }

    @Override
    public void prepareData() {
        restoreSellJournalFromCache();
        prepareOpenedLongPositions();
        subscribeToUserUpdateEvents();
    }

    private void restoreSellJournalFromCache() {
        var sellJournal = indicatorVirginStrategyCondition.getSellJournal();
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
                indicatorVirginStrategyCondition.getLongPositions().put(pos.symbol(), pos);
            }
        });

        dataService.deleteAllOpenedPositions(indicatorVirginStrategyCondition.getLongPositions().values(), this);
    }

    private void subscribeToUserUpdateEvents() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("strategyName", getName());
        parameters.put("botAddress", USER_DATA_UPDATE_ENDPOINT);
        dataService.addActiveStrategy(parameters);
    }

    @Override
    public void handleBuying(final OrderTradeUpdateEvent buyEvent) {
        log.debug("Get buy event of {} with strategy id {}", buyEvent.getSymbol(), buyEvent.getStrategyId());
        if (indicatorVirginEnabled && getId().equals(buyEvent.getStrategyId())) {
            final String symbol = buyEvent.getSymbol();

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(buyEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(symbol).getPrice())
                    : parsedFloat(buyEvent.getPrice());

            log.info("BUY {} {} at {}.",
                    buyEvent.getAccumulatedQuantity(), symbol, dealPrice);
            indicatorVirginStrategyCondition.addOpenedPosition(symbol, dealPrice, parsedFloat(buyEvent.getAccumulatedQuantity()),
                    priceDecreaseFactor, false, getName()
            );
            indicatorVirginStrategyCondition.removePositionFromMonitoring(symbol);

            marketInfo.pairOrderFilled(symbol, getName());
        }
    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent sellEvent) {
        log.debug("Get sell event of {} with strategy id {}.", sellEvent.getSymbol(), sellEvent.getStrategyId());
        if (indicatorVirginEnabled && (getId().equals(Optional.ofNullable(sellEvent.getStrategyId()).orElse(getId())))) {

            // if price == 0 most likely it was market order, use last market price.
            float dealPrice = parsedFloat(sellEvent.getPrice()) == 0
                    ? parsedFloat(marketInfo.getLastTickerPrice(sellEvent.getSymbol()).getPrice())
                    : parsedFloat(sellEvent.getPrice());

            log.info("SELL {} {} at {}.",
                    sellEvent.getOriginalQuantity(), sellEvent.getSymbol(), dealPrice);

            indicatorVirginStrategyCondition.removeOpenedPosition(sellEvent.getSymbol());
            indicatorVirginStrategyCondition.addSellRecordToJournal(sellEvent.getSymbol(), getName());

            marketInfo.pairOrderFilled(sellEvent.getSymbol(), getName());
        }
    }

    public void startCandlstickEventsCacheUpdating() {
        closeOpenedWebSocketStreams();
        AtomicInteger monitoringThreadsCounter = new AtomicInteger();

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
            candleStickEventsStreams.put(String.join(",", combinedPair),
                    marketInfo.openCandleStickEventsStream(String.join(",", combinedPair).toLowerCase(), candlestickInterval,
                            marketMonitoringCallback()));
            monitoringThreadsCounter.getAndIncrement();
        });

        log.info("Runned {} monitoring threads.", monitoringThreadsCounter);
    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback() {
        return event -> {
            addEventToBaseBarSeries(event);

            Optional.ofNullable(indicatorVirginStrategyCondition.getLongPositions().get(event.getSymbol())).ifPresentOrElse(
                    openedPosition -> analizeOpenedPosition(event, openedPosition),
                    () -> analizeMarketPosition(event));
        };
    }

    private void analizeMarketPosition(final CandlestickEvent event) {
        if (indicatorVirginStrategyCondition.pairOnMonitoring(event.getSymbol())) {
            analizeMonitoredPosition(event);
//            buyFast(event.getSymbol(), parsedFloat(event.getClose()), tradingAsset, false);
        } else if (signalToOpenLongPosition(event)) {
            indicatorVirginStrategyCondition.addPairToMonitoring(event.getSymbol(), parsedFloat(event.getClose()));
        }
    }

    private boolean signalToOpenLongPosition(final CandlestickEvent event) {
//        if (Optional.ofNullable(emulatedPositions.get(event.getSymbol())).map(AtomicBoolean::get).orElse(false)) {
//            return false;
//        }
        if (marketInfo.pairOrderIsProcessing(event.getSymbol(), getName()) || indicatorVirginStrategyCondition.thisSignalWorkedOutBefore(event.getSymbol())) {
            return false;
        }

        BaseBarSeries series = barSeriesMap.get(event.getSymbol());
        var endBarSeriesIndex = series.getEndIndex();

        SMAIndicator sma7 = new SMAIndicator(new ClosePriceIndicator(series), 7);
        SMAIndicator sma25 = new SMAIndicator(new ClosePriceIndicator(series), 25);
        RSIIndicator rsi14 = new RSIIndicator(new ClosePriceIndicator(series), 14);

        var sma7Value = sma7.getValue(endBarSeriesIndex);
        var sma25Value = sma25.getValue(endBarSeriesIndex);
        var rsi14Value = rsi14.getValue(endBarSeriesIndex);

        if (sma7Value.isGreaterThan(sma25Value) && itsSustainableGrowth(sma7, sma25, endBarSeriesIndex, 2)
                && rsi14Value.floatValue() > 73F
                && haveBreakdown(sma7, sma25, endBarSeriesIndex, 10)) {
//                && sma7Value.isLessThanOrEqual(sma25Value.multipliedBy(DoubleNum.valueOf(1.06F)))
            log.debug("SMA7 of {} ({}) is higher then SMA25 ({}) with RSI14 ({}) is greater then 72.", event.getSymbol(), sma7Value, sma25Value, rsi14Value);
            return true;
        }

        return false;
    }

    private boolean itsSustainableGrowth(final SMAIndicator sma7, final SMAIndicator sma25, final int endBarSeriesIndex, final int barsQty) {
        for (int i = endBarSeriesIndex; i > endBarSeriesIndex - barsQty; i--) {
            if (sma25.getValue(i).isGreaterThan(sma7.getValue(i))) {
                return false;
            }
        }

        return true;
    }

    private boolean haveBreakdown(final SMAIndicator sma7, final SMAIndicator sma25, final int endBarSeriesIndex, final int barsQty) {
        for (int i = endBarSeriesIndex; i > endBarSeriesIndex - barsQty; i--) {
            if (sma25.getValue(i).isGreaterThan(sma7.getValue(i))) {
                return true;
            }
        }

        return false;
    }

    private void analizeMonitoredPosition(final CandlestickEvent event) {
        Optional<Float> startPrice = indicatorVirginStrategyCondition.getMonitoredPositionPrice(event.getSymbol());
        if (startPrice.isEmpty()) {
            return;
        }
        if (parsedFloat(event.getClose()) > startPrice.get() * 1.02) {
            buyFast(event.getSymbol(), parsedFloat(event.getClose()), tradingAsset, false);
        }


    }

    private void analizeOpenedPosition(final CandlestickEvent event, final OpenedPosition openedPosition) {
        final String ticker = event.getSymbol();
        var currentPrice = parsedFloat(event.getClose());

        addEventToBaseBarSeries(event);

        indicatorVirginStrategyCondition.updateOpenedPositionLastPrice(ticker, currentPrice, indicatorVirginStrategyCondition.getLongPositions());

        if (currentPrice > openedPosition.avgPrice() * pairTakeProfitFactor) {
            openedPosition.priceDecreaseFactor(takeProfitPriceDecreaseFactor);
            if (averagingEnabled) {
                buyFast(ticker, currentPrice, tradingAsset, true);
            }
        }

        if (signalToCloseLongPosition(event, openedPosition)) {
            sellFast(event.getSymbol(), openedPosition.qty(), tradingAsset);
        }
    }

    private void buyFast(final String symbol, final float price, String quoteAsset, boolean itsAveraging) {
        if (!(marketInfo.pairOrderIsProcessing(symbol, getName()) || indicatorVirginStrategyCondition.thisSignalWorkedOutBefore(symbol))) {
//            emulateBuy(symbol, price);
//            log.debug("Price of {} growth more than {}%, and now equals {}.", symbol, Float.valueOf(100 * priceGrowthFactor - 100).intValue(), price);
            spotTrading.placeBuyOrderFast(symbol, getName(), getId(), price, quoteAsset, minimalAssetBalance, baseOrderVolume);
        }
    }

    private void emulateBuy(String ticker, float currentPrice) {
        Optional.ofNullable(emulatedPositions.get(ticker)).ifPresentOrElse(inPosition -> {
            if (!inPosition.get()) {
                inPosition.set(true);
                log.info("BUY {} at {}.", ticker, currentPrice);
            }
        }, () -> {
            emulatedPositions.put(ticker, new AtomicBoolean(true));
            log.info("BUY {} at {}.", ticker, currentPrice);
        });
    }

    private boolean signalToCloseLongPosition(final CandlestickEvent event, final OpenedPosition openedPosition) {
        if (marketInfo.pairOrderIsProcessing(event.getSymbol(), getName())) {
            return false;
        }

        BaseBarSeries series = barSeriesMap.get(event.getSymbol());
        var endBarSeriesIndex = series.getEndIndex();
        MACDIndicator macdIndicator = new MACDIndicator(new ClosePriceIndicator(series), 7, 14);


        final String ticker = event.getSymbol();
        var currentPrice = parsedFloat(event.getClose());
        float stopTriggerValue = openedPosition.priceDecreaseFactor().equals(takeProfitPriceDecreaseFactor) ? openedPosition.maxPrice() : openedPosition.avgPrice();

        if (currentPrice < stopTriggerValue * openedPosition.priceDecreaseFactor()
                && (macdIndicator.getValue(endBarSeriesIndex).isLessThanOrEqual(macdIndicator.getValue(endBarSeriesIndex - 1))
                        || series.getBar(endBarSeriesIndex).getClosePrice().isGreaterThan(series.getBar(endBarSeriesIndex).getOpenPrice().multipliedBy(DoubleNum.valueOf(1.2))))
        ) {
            log.info("PRICE of {} DECREASED and now equals {} (current MACD is {}, prev MACD is {}), price decrease factor is {} / {}.",
                    ticker, currentPrice, macdIndicator.getValue(endBarSeriesIndex), macdIndicator.getValue(endBarSeriesIndex - 1),
                    openedPosition.priceDecreaseFactor(), indicatorVirginStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());
            return true;
        }
        return false;
    }

    public void addEventToBaseBarSeries(final CandlestickEvent event) {
        Optional.ofNullable(barSeriesMap.get(event.getSymbol())).ifPresentOrElse(barSeries -> {
            if (ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getCloseTime()), ZoneId.systemDefault()).equals(barSeries.getBar(barSeries.getEndIndex()).getEndTime())) {
                              barSeries.addBar(CandlestickToBaseBarMapper.map(event, candlestickInterval), true);
            } else {
                log.debug("Close time of {} are equals? - {} : {} / {}",
                        event.getSymbol(), ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getCloseTime()), ZoneId.systemDefault()).equals(barSeries.getBar(barSeries.getEndIndex()).getEndTime()),
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getCloseTime()), ZoneId.systemDefault()), barSeries.getBar(barSeries.getEndIndex()).getEndTime());
                barSeries.addBar(CandlestickToBaseBarMapper.map(event, candlestickInterval), false);
            }
        }, () -> {
            barSeriesMap.put(event.getSymbol(),
                    newBaseBarSeries(marketInfo.getCandleSticks(event.getSymbol(), candlestickInterval, baseBarSeriesLimit), event.getSymbol()));
        });
    }

    private BaseBarSeries newBaseBarSeries(List<? extends Candle> candles, final String ticker) {
        return barSeriesBuilder
                .withBars(CandlestickToBaseBarMapper.map(candles, candlestickInterval))
                .withMaxBarCount(baseBarSeriesLimit)
                .withName(String.format("%s_%s", ticker, getName()))
                .build();
    }

    private void sellFast(String symbol, float qty, String quoteAsset) {
        if (!marketInfo.pairOrderIsProcessing(symbol, getName())) {
            spotTrading.placeSellOrderFast(symbol, getName(), getId(), qty);
        }
    }

    private void emulateSell(String symbol) {
        Optional.ofNullable(emulatedPositions.get(symbol)).ifPresent(inPosition -> {
            if (inPosition.get()) {
                inPosition.set(false);
                log.info("SELL {}.", symbol);
            }
        });
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

    private void backupOpenedPositions() {
        List<OpenedPosition> savedPositions = dataService.saveAllOpenedPositions(indicatorVirginStrategyCondition.getLongPositions().values(), this);
    }

    private void backupSellRecords() {
        dataService.saveAllSellRecords(indicatorVirginStrategyCondition.getSellJournal().values(), this);
    }

    private void unSubscribeFromUserUpdateEvents() {
        try {
            dataService.deleteActiveStrategy(getName());
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
