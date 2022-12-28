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
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;

import io.micrometer.core.annotation.Timed;
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
public class BuyBigVolumeGrowth implements TradingStrategy {

    @Getter
    final MarketInfo marketInfo;
    final MarketData marketData;
    final SpotTrading spotTrading;
    @Getter
    final AccountManager accountManager;

    final Map<String, Closeable> candleStickEventsStreams = new ConcurrentHashMap<>();
    CandlestickInterval candlestickInterval;

    @Value("${strategy.buyBigVolumeGrowth.enabled}")
    boolean buyBigVolumeGrowthEnabled;
    @Value("${strategy.buyBigVolumeGrowth.matchTrend}")
    boolean matchTrend;
    @Value("${strategy.buyBigVolumeGrowth.volumeGrowthFactor}")
    int volumeGrowthFactor;
    @Value("${strategy.monitoring.priceDecreaseFactor}")
    double priceDecreaseFactor;
    @Value("${strategy.buyBigVolumeGrowth.priceGrowthFactor}")
    double priceGrowthFactor;
    @Value("${strategy.monitoring.averagingEnabled}")
    boolean averagingEnabled;
    @Value("${strategy.monitoring.averagingTriggerFactor}")
    double averagingTriggerFactor;
    @Value("${strategy.global.rocketFactor}")
    double rocketFactor;

    private static Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    @Override
    public void handleBuying(OrderTradeUpdateEvent buyEvent) {
        if (buyBigVolumeGrowthEnabled) {
            Optional.ofNullable(candleStickEventsStreams.remove(buyEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
                try {
//                    marketData.removeCandlestickEventsCacheForPair(buyEvent.getSymbol());
                    candlestickEventsStream.close();
                } catch (IOException e) {
                    log.error("Error while trying to close candlestick event stream of '{}':\n{}", buyEvent.getSymbol(), e.getMessage());
                }
            });
            candleStickEventsStreams.put(buyEvent.getSymbol(), marketInfo.openCandleStickEventsStream(buyEvent.getSymbol().toLowerCase(), candlestickInterval,
                    longPositionMonitoringCallback(buyEvent.getSymbol(), "USDT")));
        }
    }

    @Override
    public void handleSelling(OrderTradeUpdateEvent sellEvent) {
        if (buyBigVolumeGrowthEnabled) {
            Optional.ofNullable(candleStickEventsStreams.remove(sellEvent.getSymbol())).ifPresent(candlestickEventsStream -> {
                try {
//                    marketData.removeCandlestickEventsCacheForPair(sellEvent.getSymbol());
                    candlestickEventsStream.close();
                } catch (IOException e) {
                    log.error("Error while trying to close candlestick event stream of '{}':\n{}", sellEvent.getSymbol(), e.getMessage());
                }
            });
            candleStickEventsStreams.put(sellEvent.getSymbol(), marketInfo.openCandleStickEventsStream(sellEvent.getSymbol().toLowerCase(), candlestickInterval,
                    marketMonitoringCallback(sellEvent.getSymbol(), "USDT")));
        }
    }

    public void startCandlstickEventsCacheUpdating(String asset, CandlestickInterval interval) {
        candlestickInterval = interval;
        closeOpenedWebSocketStreams();

        marketData.getCheapPairsExcludeOpenedPositions(asset)
            .forEach(ticker -> {
                candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    marketMonitoringCallback(ticker, asset)));
            });
        marketData.getLongPositions().forEach((ticker, openedPosition) -> {
            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    longPositionMonitoringCallback(ticker, asset)));
        });
    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback(String ticker, String asset) {
        return event -> {
            marketData.addCandlestickEventToCache(ticker, event);

            var currentEvent = marketData.getCachedCandleStickEvents().get(ticker).getLast();
            var previousEvent = marketData.getCachedCandleStickEvents().get(ticker).getFirst();

            if (parsedDouble(currentEvent.getVolume()) > parsedDouble(previousEvent.getVolume()) * volumeGrowthFactor
                    && parsedDouble(currentEvent.getClose()) > parsedDouble(previousEvent.getClose()) * priceGrowthFactor) {
                buyFast(ticker, parsedDouble(currentEvent.getClose()), asset);
            }
        };
    }

    private BinanceApiCallback<CandlestickEvent> longPositionMonitoringCallback(String ticker, String asset) {
        return event -> {
            marketData.addCandlestickEventToCache(ticker, event);

            List<AssetBalance> currentBalances = accountManager.getAccountBalances().stream()
                    .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();

            if (currentBalances.isEmpty()) {
                log.warn("No available trading assets found on binance account, but long position monitoring for '{}' is still executing.", ticker);
                return;
            }

            Optional.ofNullable(marketData.getLongPositions().get(ticker)).ifPresent(openedPosition -> {
                Double assetPrice = parsedDouble(event.getClose());

                if (assetPrice > openedPosition.maxPrice()) { // update current price if it's growth.
                    marketData.updateOpenedPosition(ticker, assetPrice, marketData.getLongPositions());
                }
                if (averagingEnabled && assetPrice > openedPosition.avgPrice() * averagingTriggerFactor) {
                    log.debug("PRICE of {} GROWTH more than avg and now equals {}.", ticker, assetPrice);
                    buyFast(ticker, assetPrice, asset);
                } else if (assetPrice < openedPosition.maxPrice() * priceDecreaseFactor) {
                    log.debug("PRICE of {} DECREASED and now equals {}.", ticker, assetPrice);
                    sellFast(ticker, openedPosition.qty(), asset);
                }
            });
        };
    }

    private void buyFast(String symbol, Double price, String quoteAsset) {
        if (!marketInfo.pairOrderIsProcessing(symbol)) {
            spotTrading.placeBuyOrderFast(symbol, price, quoteAsset, accountManager);
        }
    }

    private void sellFast(String symbol, Double qty, String quoteAsset) {
        if (marketInfo.pairOrderIsProcessing(symbol)) {
            return;
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
            spotTrading.placeSellOrderFast(symbol, qty);
//        }
    }

    /**
     * Get current opened long positions from account and close their streams of monitoring candle stick events.
     */
    public void stopMonitorOpenedLongPositions() {
        marketData.getLongPositions().keySet().forEach(pair -> {
            Optional.ofNullable(candleStickEventsStreams.remove(pair)).ifPresent(stream -> {
                try {
                    stream.close();
                } catch (IOException e) {
                    log.error("Error while trying to close candlestick event stream of '{}':\n{}", pair, e.getMessage());
                }
            });
        });
    }

    // compare volumes in current and previous candles to find big volume growth.
    @Timed("findGrownAssets")
    public void findGrownAssets() {
        Map<String, List<Candlestick>> cachedCandlesticks = marketData.getCachedCandles();
        Map<String, Deque<CandlestickEvent>> cachedCandleStickEvents = marketData.getCachedCandleStickEvents();

        try {
            cachedCandlesticks.entrySet().stream() // current volume & current price bigger than previous:
                    .filter(entrySet -> parsedDouble(entrySet.getValue().get(1)
                            .getVolume()) > parsedDouble(entrySet.getValue().get(0).getVolume()) * volumeGrowthFactor
                            && parsedDouble(entrySet.getValue().get(1).getClose()) > parsedDouble(
                            entrySet.getValue().get(0).getClose()) * priceGrowthFactor)
                    .forEach(entrySet -> addPairToBuy(entrySet.getKey(),
                            parsedDouble(entrySet.getValue().get(1).getClose())));
        } catch (Exception e) {
            log.error("Error while trying to find grown assets:\n{}.", e.getMessage());
            e.printStackTrace();
        }

//        Map<String, List<Candlestick>> cachedCandlesticks = marketData.getCachedCandles();
//
//        try {
//            cachedCandlesticks.entrySet().stream() // current volume & current price bigger than previous:
//                    .filter(entrySet -> parsedDouble(entrySet.getValue().get(1)
//                            .getVolume()) > parsedDouble(entrySet.getValue().get(0).getVolume()) * volumeGrowthFactor
//                            && parsedDouble(entrySet.getValue().get(1).getClose()) > parsedDouble(
//                            entrySet.getValue().get(0).getClose()) * priceGrowthFactor)
//                    .forEach(entrySet -> addPairToBuy(entrySet.getKey(),
//                            parsedDouble(entrySet.getValue().get(1).getClose())));
//        } catch (Exception e) {
//            log.error("Error while trying to find grown assets:\n{}.", e.getMessage());
//            e.printStackTrace();
//        }
    }

    public void addPairToBuy(String symbol, Double price) {
        if (matchTrend) {
            List<Candlestick> candleSticks = marketInfo.getCandleSticks(symbol, CandlestickInterval.DAILY, 2);
            if (marketInfo.pairHadTradesInThePast(candleSticks, 2)
                    && price > parsedDouble(candleSticks.get(0).getHigh())) {
                marketData.putPairToBuy(symbol, price);
            }
        } else {
            marketData.putPairToBuy(symbol, price);
        }
    }

    @Timed("buyGrownAssets")
    public void buyGrownAssets(String quoteAsset) {
        var pairsToBuy = marketData.getPairsToBuy();
        log.debug("There is {} pairs to buy: {}.", pairsToBuy.size(), pairsToBuy);

        spotTrading.buyAssets(pairsToBuy, quoteAsset, accountManager);
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
