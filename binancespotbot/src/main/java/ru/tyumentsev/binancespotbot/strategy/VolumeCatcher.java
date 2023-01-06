package ru.tyumentsev.binancespotbot.strategy;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.CandlestickEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.market.CandlestickInterval;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.service.AccountManager;
import ru.tyumentsev.binancespotbot.service.MarketInfo;
import ru.tyumentsev.binancespotbot.service.SpotTrading;

import javax.annotation.PreDestroy;

/**
 * This strategy will get two last candlesticks for each quote USDT pair
 * and buy this asset if volume has grown more than priceGrowthFactor against previous candle.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class VolumeCatcher implements TradingStrategy {

    @Getter
    final MarketInfo marketInfo;
    final MarketData marketData;
    final SpotTrading spotTrading;
    @Getter
    final AccountManager accountManager;

    // stores current and previous candlestick events for each pair to compare them.
    // first element - previous, last element - current.
    @Getter
    final Map<String, Deque<CandlestickEvent>> cachedCandlestickEvents = new ConcurrentHashMap<>();
    @Getter
    final Map<String, Closeable> candleStickEventsStreams = new ConcurrentHashMap<>();
    CandlestickInterval candlestickInterval;

    @Value("${strategy.volumeCatcher.enabled}")
    boolean volumeCatherEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.volumeCatcher.matchTrend}")
    boolean matchTrend;
    @Value("${strategy.volumeCatcher.volumeGrowthFactor}")
    int volumeGrowthFactor;
    @Value("${strategy.volumeCatcher.priceDecreaseFactor}")
    double priceDecreaseFactor;
    @Value("${strategy.volumeCatcher.priceGrowthFactor}")
    double priceGrowthFactor;
    @Value("${strategy.volumeCatcher.averagingEnabled}")
    boolean averagingEnabled;
    @Value("${strategy.volumeCatcher.averagingTriggerFactor}")
    double averagingTriggerFactor;
    @Value("${strategy.global.rocketFactor}")
    double rocketFactor;
    @Value("${strategy.volumeCatcher.signalIgnoringPeriod}")
    long signalIgnoringPeriod;

    @Override
    public boolean isEnabled() {
        return volumeCatherEnabled;
    }

    @Override
    public void prepareData() {
        marketData.constructCandleStickEventsCache(tradingAsset, cachedCandlestickEvents);
        marketData.getLongPositions().values().forEach(openedPosition -> openedPosition.priceDecreaseFactor(priceDecreaseFactor));
    }

    @Override
    public void handleBuying(final OrderTradeUpdateEvent buyEvent) {
        if (volumeCatherEnabled
                && parsedDouble(buyEvent.getAccumulatedQuantity()) == (parsedDouble(buyEvent.getOriginalQuantity()))) {
            // if price == 0 most likely it was market order, use last market price.
            Double dealPrice = parsedDouble(buyEvent.getPrice()) == 0
                    ? parsedDouble(marketInfo.getLastTickerPrice(buyEvent.getSymbol()).getPrice())
                    : parsedDouble(buyEvent.getPrice());

            log.info("BUY order trade updated, put result in opened positions cache: buy {} {} at {}.",
                    buyEvent.getOriginalQuantity(), buyEvent.getSymbol(), dealPrice);
            marketData.putLongPositionToPriceMonitoring(buyEvent.getSymbol(), dealPrice, parsedDouble(buyEvent.getOriginalQuantity()), priceDecreaseFactor);
            marketInfo.pairOrderFilled(buyEvent.getSymbol());

            Optional.ofNullable(candleStickEventsStreams.remove(buyEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
                try {
                    candlestickEventsStream.close();
                } catch (IOException e) {
                    log.error("Error while trying to close candlestick event stream of '{}':\n{}", buyEvent.getSymbol(), e.getMessage());
                } finally {
                    marketData.removeCandlestickEventsCacheForPair(buyEvent.getSymbol(), cachedCandlestickEvents);
                }
            });
            candleStickEventsStreams.put(buyEvent.getSymbol(), marketInfo.openCandleStickEventsStream(buyEvent.getSymbol().toLowerCase(), candlestickInterval,
                    longPositionMonitoringCallback(buyEvent.getSymbol())));
        }
    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent sellEvent) {
        if (volumeCatherEnabled
                && parsedDouble(sellEvent.getAccumulatedQuantity()) == (parsedDouble(sellEvent.getOriginalQuantity()))) {
            // if price == 0 most likely it was market order, use last market price.
            Double dealPrice = parsedDouble(sellEvent.getPrice()) == 0
                    ? parsedDouble(marketInfo.getLastTickerPrice(sellEvent.getSymbol()).getPrice())
                    : parsedDouble(sellEvent.getPrice());

            log.info("SELL order trade updated, remove result from opened positions cache: sell {} {} at {}.",
                    sellEvent.getOriginalQuantity(), sellEvent.getSymbol(), dealPrice);

            marketData.removeLongPositionFromPriceMonitoring(sellEvent.getSymbol());
            marketInfo.pairOrderFilled(sellEvent.getSymbol());

//            marketData.addSellRecordToJournal(sellEvent.getSymbol());
            Optional.ofNullable(candleStickEventsStreams.remove(sellEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
                try {
//                    marketData.removeCandlestickEventsCacheForPair(sellEvent.getSymbol());
                    candlestickEventsStream.close();
                } catch (IOException e) {
                    log.error("Error while trying to close candlestick event stream of '{}':\n{}", sellEvent.getSymbol(), e.getMessage());
                }
            });
            candleStickEventsStreams.put(sellEvent.getSymbol(), marketInfo.openCandleStickEventsStream(sellEvent.getSymbol().toLowerCase(), candlestickInterval,
                    marketMonitoringCallback(sellEvent.getSymbol())));
        }
    }

    public void startCandlstickEventsCacheUpdating(String asset, CandlestickInterval interval) {
        candlestickInterval = interval;
        closeOpenedWebSocketStreams();

        marketData.getCheapPairsExcludeOpenedPositions(asset)
            .forEach(ticker -> candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                marketMonitoringCallback(ticker))));
        marketData.getLongPositions().forEach((ticker, openedPosition) -> candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                longPositionMonitoringCallback(ticker))));
    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback(String ticker) {
        return event -> {
            marketData.addCandlestickEventToCache(ticker, event, cachedCandlestickEvents);
//            log.info("Callback in VolumeCatcher of {}, close = {}, open = {}, priceGrowthFactor = {}."
//                    , ticker, event.getClose(), event.getOpen(), priceGrowthFactor);

            var currentEvent = cachedCandlestickEvents.get(ticker).getLast();
            var previousEvent = cachedCandlestickEvents.get(ticker).getFirst();

            if (parsedDouble(currentEvent.getVolume()) > parsedDouble(previousEvent.getVolume()) * volumeGrowthFactor
                    && parsedDouble(currentEvent.getClose()) > parsedDouble(previousEvent.getClose()) * priceGrowthFactor) {
                buyFast(ticker, parsedDouble(currentEvent.getClose()), tradingAsset);
            }
        };
    }

    private BinanceApiCallback<CandlestickEvent> longPositionMonitoringCallback(String ticker) {
        return event -> {
            marketData.addCandlestickEventToCache(ticker, event, cachedCandlestickEvents);

            List<AssetBalance> currentBalances = accountManager.getAccountBalances().stream()
                    .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();

            if (currentBalances.isEmpty()) {
                log.warn("No available trading assets found on binance account, but long position monitoring for '{}' is still executing.", ticker);
                return;
            }

            Optional.ofNullable(marketData.getLongPositions().get(ticker)).ifPresent(openedPosition -> {
                var assetPrice = parsedDouble(event.getClose());

//                if (assetPrice > openedPosition.maxPrice()) { // update current price if it's growth.
                    marketData.updateOpenedPosition(ticker, assetPrice, marketData.getLongPositions());
//                }
                if (assetPrice < openedPosition.maxPrice() * openedPosition.priceDecreaseFactor()) {
                    log.debug("PRICE of {} DECREASED and now equals {}.", ticker, assetPrice);
                    sellFast(ticker, openedPosition.qty(), tradingAsset);
                } else if (averagingEnabled && assetPrice > openedPosition.avgPrice() * averagingTriggerFactor) {
                    log.debug("PRICE of {} GROWTH more than avg and now equals {}.", ticker, assetPrice);
                    buyFast(ticker, assetPrice, tradingAsset);
                }
            });
        };
    }

    private void buyFast(String symbol, Double price, String quoteAsset) {
        if (!(marketInfo.pairOrderIsProcessing(symbol)
//                || marketData.thisSignalWorkedOutBefore(symbol, signalIgnoringPeriod)
        )) {
            marketInfo.pairOrderPlaced(symbol);
            spotTrading.placeBuyOrderFast(symbol, price, quoteAsset, accountManager);
        }
    }

    private void sellFast(String symbol, Double qty, String quoteAsset) {
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

    public void closeOpenedWebSocketStreams() {
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

    //    /**
//     * Add to cache information about last candles of all filtered pairs (exclude
//     * opened positions).
//     *
//     * @param asset
//     * @param interval
//     * @param limit
//     */
//    @Timed("updateMonitoredCandles")
//    public void updateMonitoredCandles(String asset, CandlestickInterval interval, Integer limit) {
//        marketData.clearCandleSticksCache();
//        marketData.getCheapPairsExcludeOpenedPositions(asset)
//                .forEach(ticker -> marketData.addCandlesticksToCache(ticker,
//                        marketInfo.getCandleSticks(ticker, interval, limit)));
//        log.debug("Cache of candle sticks updated and now contains {} pairs.", marketData.getCachedCandles().size());
//    }

}
