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
import ru.tyumentsev.binancespotbot.mapping.CandlestickToEventMapper;
import ru.tyumentsev.binancespotbot.service.AccountManager;
import ru.tyumentsev.binancespotbot.service.MarketInfo;
import ru.tyumentsev.binancespotbot.service.SpotTrading;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class Daily implements TradingStrategy {

    final MarketInfo marketInfo;
    final MarketData marketData;
    final SpotTrading spotTrading;
    final AccountManager accountManager;

    CandlestickInterval candlestickInterval;
    @Getter
    final Map<String, Closeable> candleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    final Map<String, Deque<CandlestickEvent>> cachedCandlestickEvents = new ConcurrentHashMap<>();
    final Map<String, LocalDateTime> sellJournal = new ConcurrentHashMap<>();
    final LocalTime eveningStopTime = LocalTime.of(22, 0);
    final LocalTime nightStopTime = LocalTime.of(3, 5);

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
    @Value("${strategy.daily.takeProfitFactor}")
    double takeProfitFactor;
    @Value("${strategy.daily.priceDecreaseFactor}")
    double priceDecreaseFactor;
    @Value("${strategy.daily.takeProfitPriceDecreaseFactor}")
    double takeProfitPriceDecreaseFactor;
    @Value("${strategy.daily.averagingTriggerFactor}")
    double averagingTriggerFactor;

    @Override
    public boolean isEnabled() {
        return dailyEnabled;
    }

    @Override
    public void prepareData() {
        marketData.constructCandleStickEventsCache(tradingAsset, cachedCandlestickEvents);

        Optional.ofNullable(marketData.getCheapPairs().get(tradingAsset)).ifPresent(pairs -> pairs.stream().map(symbol -> {
                    var candlestick = marketInfo.getCandleSticks(symbol, CandlestickInterval.DAILY, 2).get(0);
                    return CandlestickToEventMapper.map(symbol, candlestick);
                }).filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(event -> marketData.addCandlestickEventToCache(event.getSymbol(), event, cachedCandlestickEvents)
                ));

        log.info("[DAILY] prepared candlestick events of {} pairs.", cachedCandlestickEvents.values().stream().filter(value -> !value.isEmpty()).count());

        marketData.getLongPositions().values().forEach(openedPosition -> openedPosition.priceDecreaseFactor(priceDecreaseFactor));
    }

    @Override
    public void handleBuying(final OrderTradeUpdateEvent buyEvent) {
        if (dailyEnabled
                && parsedDouble(buyEvent.getAccumulatedQuantity()) == parsedDouble(buyEvent.getOriginalQuantity())) {
            // if price == 0 most likely it was market order, use last market price.
            double dealPrice = parsedDouble(buyEvent.getPrice()) == 0
                    ? parsedDouble(marketInfo.getLastTickerPrice(buyEvent.getSymbol()).getPrice())
                    : parsedDouble(buyEvent.getPrice());

            log.info("[DAILY] BUY {} {} at {}.",
                    buyEvent.getOriginalQuantity(), buyEvent.getSymbol(), dealPrice);
            marketData.putLongPositionToPriceMonitoring(buyEvent.getSymbol(), dealPrice, parsedDouble(buyEvent.getOriginalQuantity()), priceDecreaseFactor);
            marketInfo.pairOrderFilled(buyEvent.getSymbol());

            Optional.ofNullable(candleStickEventsStreams.remove(buyEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
                try {
                    candlestickEventsStream.close();
                } catch (IOException e) {
                    log.error("[DAILY] Error while trying to close candlestick event stream of '{}':\n{}", buyEvent.getSymbol(), e.getMessage());
//                } finally {
//                    marketData.removeCandlestickEventsCacheForPair(buyEvent.getSymbol(), cachedCandlestickEvents);
                }
            });
            candleStickEventsStreams.put(buyEvent.getSymbol(), marketInfo.openCandleStickEventsStream(buyEvent.getSymbol().toLowerCase(), candlestickInterval,
                    longPositionMonitoringCallback(buyEvent.getSymbol())));
        }
    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent sellEvent) {
        if (dailyEnabled
                && parsedDouble(sellEvent.getAccumulatedQuantity()) == (parsedDouble(sellEvent.getOriginalQuantity()))) {
            // if price == 0 most likely it was market order, use last market price.
            double dealPrice = parsedDouble(sellEvent.getPrice()) == 0
                    ? parsedDouble(marketInfo.getLastTickerPrice(sellEvent.getSymbol()).getPrice())
                    : parsedDouble(sellEvent.getPrice());

            log.info("[DAILY] SELL {} {} at {}.",
                    sellEvent.getOriginalQuantity(), sellEvent.getSymbol(), dealPrice);

            marketData.removeLongPositionFromPriceMonitoring(sellEvent.getSymbol());

            addSellRecordToJournal(sellEvent.getSymbol());
            Optional.ofNullable(candleStickEventsStreams.remove(sellEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
                try {
//                    marketData.removeCandlestickEventsCacheForPair(sellEvent.getSymbol());
                    candlestickEventsStream.close();
                } catch (IOException e) {
                    log.error("[DAILY] Error while trying to close candlestick event stream of '{}':\n{}", sellEvent.getSymbol(), e.getMessage());
                }
            });

            marketInfo.pairOrderFilled(sellEvent.getSymbol());
            candleStickEventsStreams.put(sellEvent.getSymbol(), marketInfo.openCandleStickEventsStream(sellEvent.getSymbol().toLowerCase(), candlestickInterval,
                    marketMonitoringCallback(sellEvent.getSymbol())));
        }
    }

    public void startCandlstickEventsCacheUpdating(String asset, CandlestickInterval interval) {
        candlestickInterval = interval;
        closeOpenedWebSocketStreams();
//        actualizeSellRecordJournal();

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
                    && parsedDouble(currentEvent.getVolume()) > parsedDouble(previousEvent.getVolume()) * volumeGrowthFactor
                    && percentageDifference(parsedDouble(event.getHigh()), closePrice) < 5 // candle's max price not much higher than current.
            ) {
                buyFast(ticker, closePrice, tradingAsset);
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
                var assetPrice = parsedDouble(event.getClose());

                marketData.updateOpenedPosition(ticker, assetPrice, marketData.getLongPositions());
                if (assetPrice > parsedDouble(event.getOpen()) * takeProfitFactor
                    && openedPosition.priceDecreaseFactor() != takeProfitPriceDecreaseFactor) {
                    openedPosition.priceDecreaseFactor(takeProfitPriceDecreaseFactor);
                }
                if (assetPrice < openedPosition.maxPrice() * openedPosition.priceDecreaseFactor()) {
                    log.debug("[DAILY] PRICE of {} DECREASED and now equals {}.", ticker, assetPrice);
                    sellFast(ticker, openedPosition.qty(), tradingAsset);
                } else if (averagingEnabled && assetPrice > openedPosition.avgPrice() * averagingTriggerFactor) {
                    log.debug("[DAILY] PRICE of {} GROWTH more than avg and now equals {}.", ticker, assetPrice);
                    buyFast(ticker, assetPrice, tradingAsset);
                }
            });
        };
    }

    private void buyFast(String symbol, double price, String quoteAsset) {
        if (//itsDealsAllowedPeriod(LocalTime.now()) &&
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
        sellJournal.put(pair, LocalDateTime.now());
    }

    /**
     * Remove from sell record journal all entries, that were added before today.
     */
    private void actualizeSellRecordJournal() {
        int today = LocalDateTime.now().getDayOfYear();
        List<String> expiredEntries = sellJournal.entrySet().stream()
                .filter((entry) -> entry.getValue().getDayOfYear() < today)
                .map(Map.Entry::getKey).toList();
        expiredEntries.forEach(sellJournal::remove);
    }

    private boolean thisSignalWorkedOutBefore (String pair) {
        AtomicBoolean ignoreSignal = new AtomicBoolean(false);

        Optional.ofNullable(sellJournal.get(pair)).ifPresent(sellTime -> {
            if (sellTime.getDayOfYear() == LocalDateTime.now().getDayOfYear()) {
                ignoreSignal.set(true);
            } else {
                log.info("[DAILY] Period of signal ignoring for {} expired, remove pair from sell journal.", pair);
                sellJournal.remove(pair);
            }
        });

        return ignoreSignal.get();
    }

    private boolean itsDealsAllowedPeriod (final LocalTime time) {
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

    @PreDestroy
    public void destroy() {
        closeOpenedWebSocketStreams();
    }
}
