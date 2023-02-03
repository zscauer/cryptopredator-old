package ru.tyumentsev.cryptopredator.indicatorvirginbot.strategy;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.Candle;
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
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.MACDIndicator;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class IndicatorVirgin implements TradingStrategy {

    final MarketInfo marketInfo;
    final IndicatorVirginStrategyCondition indicatorVirginStrategyCondition;
    final SpotTrading spotTrading;
    final DataService dataService;

    final CandlestickInterval candlestickInterval = CandlestickInterval.FIFTEEN_MINUTES;
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

    final Map<String, AtomicBoolean> emulatedPositions = new ConcurrentHashMap<>();

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.indicatorVirgin.enabled}")
    boolean indicatorVirginEnabled;
    @Value("${strategy.indicatorVirgin.averagingEnabled}")
    boolean averagingEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.indicatorVirgin.volumeGrowthFactor}")
    float volumeGrowthFactor;
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
//        restoreSellJournalFromCache();
//        prepareOpenedLongPositions();
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

    @Override
    public void handleBuying(OrderTradeUpdateEvent orderTradeUpdateEvent) {

    }

    @Override
    public void handleSelling(OrderTradeUpdateEvent orderTradeUpdateEvent) {

    }

    public void startCandlstickEventsCacheUpdating() {
        closeOpenedWebSocketStreams();
        AtomicInteger marketMonitoringThreadsCounter = new AtomicInteger();
        AtomicInteger longMonitoringThreadsCounter = new AtomicInteger();

        marketInfo.getCheapPairsExcludeOpenedPositions(tradingAsset, indicatorVirginStrategyCondition.getLongPositions().keySet(), indicatorVirginStrategyCondition.getShortPositions().keySet()).forEach(ticker -> {
            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    marketMonitoringCallback()));
            marketMonitoringThreadsCounter.getAndIncrement();
        });
        indicatorVirginStrategyCondition.getLongPositions().forEach((ticker, openedPosition) -> {
            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    longPositionMonitoringCallback()));
            longMonitoringThreadsCounter.getAndIncrement();
        });

        log.info("Runned {} market monitoring threads and {} long monitoring threads.", marketMonitoringThreadsCounter, longMonitoringThreadsCounter);
    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback() {
        return event -> {
            addEventToBaseBarSeries(event);

            if (signalToOpenLongPosition(event)) {
                buyFast(event.getSymbol(), parsedFloat(event.getClose()), tradingAsset, false);
            }
        };
    }

    private boolean signalToOpenLongPosition(final CandlestickEvent event) {
        if (Optional.ofNullable(emulatedPositions.get(event.getSymbol())).map(AtomicBoolean::get).orElse(false)) {
            return false;
        }
        BaseBarSeries series = barSeriesMap.get(event.getSymbol());
        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series));

        var endBarSeriesIndex = series.getEndIndex();
        var currentMACDValue = macd.getValue(series.getEndIndex());

        if (series.getBar(endBarSeriesIndex).getVolume().isGreaterThan(
                        series.getBar(endBarSeriesIndex - 1).getVolume().multipliedBy(DoubleNum.valueOf(volumeGrowthFactor)))
                && series.getBar(endBarSeriesIndex).isBullish()) {
            if (currentMACDValue.isPositive() && macd.getShortTermEma().getValue(endBarSeriesIndex).isPositive()) {
                log.info("MACD of {} is positive ({}): short - {} / long - {}.", event.getSymbol(), currentMACDValue, macd.getShortTermEma().getValue(endBarSeriesIndex), macd.getLongTermEma().getValue(endBarSeriesIndex));
                return true;
            }
        }
        return false;
    }

    private BinanceApiCallback<CandlestickEvent> longPositionMonitoringCallback() {
        return event -> {
            final String ticker = event.getSymbol();
            addEventToBaseBarSeries(event);

            List<AssetBalance> currentBalances = spotTrading.getAccountBalances().stream()
                    .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();

            if (currentBalances.isEmpty()) {
                log.warn("No available trading assets found on binance account, but long position monitoring for '{}' is still executing.", ticker);
                return;
            }

            Optional.ofNullable(indicatorVirginStrategyCondition.getLongPositions().get(ticker)).ifPresent(openedPosition -> {
                var currentPrice = parsedFloat(event.getClose());

                indicatorVirginStrategyCondition.updateOpenedPositionLastPrice(ticker, currentPrice, indicatorVirginStrategyCondition.getLongPositions());

                if (currentPrice > openedPosition.avgPrice() * pairTakeProfitFactor) {
                    log.info("Current price decrease factor of {} is {}.", openedPosition.symbol(), indicatorVirginStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());
//                    marketData.updatePriceDecreaseFactor(ticker, takeProfitPriceDecreaseFactor, marketData.getLongPositions());
                    openedPosition.priceDecreaseFactor(takeProfitPriceDecreaseFactor);
                    log.info("Price decrease factor of {} after changing is {}.", openedPosition.symbol(), indicatorVirginStrategyCondition.getLongPositions().get(openedPosition.symbol()).priceDecreaseFactor());

                    if (averagingEnabled) {
                        buyFast(ticker, currentPrice, tradingAsset, true);
                    }
                }

                if (signalToCloseLongPosition(event)) {
                    sellFast(event.getSymbol(), openedPosition.qty(), tradingAsset);
                }
            });
        };
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

    private void emulateSell(String symbol) {
        Optional.ofNullable(emulatedPositions.get(symbol)).ifPresent(inPosition -> {
            if (inPosition.get()) {
                inPosition.set(false);
                log.info("SELL {}.", symbol);
            }
        });
    }

    private boolean signalToCloseLongPosition(final CandlestickEvent event) {
        if (!Optional.ofNullable(emulatedPositions.get(event.getSymbol())).map(AtomicBoolean::get).orElse(false)) {
            return false;
        }
        BaseBarSeries series = barSeriesMap.get(event.getSymbol());
        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series));

        var endBarSeriesIndex = series.getEndIndex();
        var currentMACDValue = macd.getValue(series.getEndIndex());


        if (currentMACDValue.isNegative()
                && macd.getShortTermEma().getValue(endBarSeriesIndex).isLessThan(macd.getLongTermEma().getValue(endBarSeriesIndex))) {
            log.info("MACD of {} is negative ({}): short - {} / long - {}.", event.getSymbol(), currentMACDValue, macd.getShortTermEma().getValue(endBarSeriesIndex), macd.getLongTermEma().getValue(endBarSeriesIndex));
            return true;
        }
        return false;
    }

    public void addEventToBaseBarSeries(final CandlestickEvent event) {
        Optional.ofNullable(barSeriesMap.get(event.getSymbol())).ifPresentOrElse(barSeries -> {
                barSeries.addBar( // replace last bar if open time is equals.
                        CandlestickToBaseBarMapper.map(event, candlestickInterval),
                        event.getOpenTime().equals(barSeries.getBar(baseBarSeriesLimit - 1).getBeginTime().toEpochSecond())
                );
        }, () -> {
            barSeriesMap.put(event.getSymbol(), newBaseBarSeries(
                    marketInfo.getCandleSticks(event.getSymbol(), candlestickInterval, baseBarSeriesLimit)));
        });
    }

    private BaseBarSeries newBaseBarSeries(List<? extends Candle> candles) {
        return barSeriesBuilder.withBars(CandlestickToBaseBarMapper.map(candles, candlestickInterval)).build();
    }

    private void buyFast(final String symbol, final float price, String quoteAsset, boolean itsAveraging) {
        if (!(marketInfo.pairOrderIsProcessing(symbol, getName()) || indicatorVirginStrategyCondition.thisSignalWorkedOutBefore(symbol))) {
            emulateBuy(symbol, price);
//            spotTrading.placeBuyOrderFast(symbol, getName(), getId(), price, quoteAsset);
//            log.info("BUY {} at {}.", symbol, price);
        }
    }

    private void sellFast(String symbol, float qty, String quoteAsset) {
        if (!marketInfo.pairOrderIsProcessing(symbol, getName())) {
            emulateSell(symbol);
//            log.info("SELL {}.", symbol);
//            spotTrading.placeSellOrderFast(symbol, getName(), getId(), qty);
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

    private void backupSellRecords() {
//        dataService.saveAllSellRecords(indicatorVirginStrategyCondition.getSellJournal().values());
    }

    private void backupOpenedPositions() {
//        dataService.saveAllOpenedPositions(indicatorVirginStrategyCondition.getLongPositions().values());
    }

    @PreDestroy
    public void destroy() {
        closeOpenedWebSocketStreams();
        backupSellRecords();
        backupOpenedPositions();
    }
}
