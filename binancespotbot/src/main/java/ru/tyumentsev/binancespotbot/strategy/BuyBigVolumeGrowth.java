package ru.tyumentsev.binancespotbot.strategy;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
 * and buy this asset if volume has grown more then priceGrowthFactor against previous candle.
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
    @Value("${strategy.buyBigVolumeGrowth.priceGrowthFactor}")
    double priceGrowthFactor;

    private static Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    @Override
    public void handleBuying(OrderTradeUpdateEvent event) {
        if (buyBigVolumeGrowthEnabled) {
            Optional.ofNullable(candleStickEventsStreams.remove(event.getSymbol())).ifPresent(stream -> {
                try {
                    stream.close();
                } catch (IOException e) {
                    log.error("Error while trying to close candlestick event stream of '{}':\n{}", event.getSymbol(), e.getMessage());
                }
            });
        }
    }

    @Override
    public void handleSelling(OrderTradeUpdateEvent sellEvent) {
        if (buyBigVolumeGrowthEnabled) {
            candleStickEventsStreams.put(sellEvent.getSymbol(), marketInfo.openCandleStickEventsStream(sellEvent.getSymbol().toLowerCase(), candlestickInterval,
                event -> {
                    marketData.addCandlestickEventToCache(sellEvent.getSymbol(), event);

                    var currentEvent = marketData.getCachedCandleStickEvents().get(sellEvent.getSymbol()).getLast();
                    var previousEvent = marketData.getCachedCandleStickEvents().get(sellEvent.getSymbol()).getFirst();

                    if (parsedDouble(currentEvent.getVolume()) > parsedDouble(previousEvent.getVolume()) * volumeGrowthFactor
                            && parsedDouble(currentEvent.getClose()) > parsedDouble(previousEvent.getClose()) * priceGrowthFactor) {
                        addPairToBuy(sellEvent.getSymbol(), parsedDouble(currentEvent.getClose()));
                    }
                }));
        }
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

    public void startCandlstickEventsCacheUpdating(String asset, CandlestickInterval interval) {
        candlestickInterval = interval;
        closeOpenedWebSocketStreams();

        marketData.getCheapPairsExcludeOpenedPositions(asset)
            .forEach(ticker -> {
                candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    event -> {
                        marketData.addCandlestickEventToCache(ticker, event);

                        var currentEvent = marketData.getCachedCandleStickEvents().get(ticker).getLast();
                        var previousEvent = marketData.getCachedCandleStickEvents().get(ticker).getFirst();

                        if (parsedDouble(currentEvent.getVolume()) > parsedDouble(previousEvent.getVolume()) * volumeGrowthFactor
                                && parsedDouble(currentEvent.getClose()) > parsedDouble(previousEvent.getClose()) * priceGrowthFactor) {
                            addPairToBuy(ticker, parsedDouble(currentEvent.getClose()));
                        }
                    }));
            });
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
        log.info("There is {} pairs to buy: {}.", pairsToBuy.size(), pairsToBuy);

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
}
