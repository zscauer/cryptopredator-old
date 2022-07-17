package ru.tyumentsev.binancetestbot.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.service.MarketInfo;

@Service
@RequiredArgsConstructor
@Log4j2
public class BuyBigVolumeGrowth {

    /*
     * this strategy will get two last candlesticks for each quote USDT pair
     * and buy this coin if volume has grown more then 3x against last candle.
     */

    @Autowired
    MarketInfo marketInfo;
    @Autowired
    MarketData marketData;
    @Autowired
    BinanceApiWebSocketClient binanceApiWebSocketClient;

    public void fillCheapPairs(String asset) {
        // get all pairs, that trades against USDT.
        List<String> availablePairs = marketData.getAvailablePairs(asset);
        // filter available pairs to get cheaper then 1 USDT.
        List<String> filteredPairs = marketInfo
                .getLastTickersPrices(
                        marketData.getAvailablePairsSymbolsFormatted(availablePairs, 0, availablePairs.size() - 1))
                .stream().filter(tickerPrice -> Double.parseDouble(tickerPrice.getPrice()) < 1)
                .map(TickerPrice::getSymbol).collect(Collectors.toCollection(ArrayList::new));
        log.info("Filtered " + filteredPairs.size() + " cheap tickers.");

        // place filtered pairs to cache.
        marketData.putCheapPairs(asset, filteredPairs);
    }

    /**
     * Add to cache information about last candles of all filtered pairs(exclude
     * opened positions).
     * 
     * @param asset
     * @param interval
     * @param limit
     */
    public void updateMonitoredCandles(String asset, CandlestickInterval interval, Integer limit) {
        marketData.clearCandleSticksCache();
        marketData.getCheapPairs(asset).stream().forEach(ticker -> {
            marketData.addCandlesticksToCache(ticker,
                    marketInfo.getCandleSticks(ticker, interval, limit));
        });
    }

    // public Closeable updateMonitoredCandleSticks(List<String> pairs) {
    // // get candlesticks cache to compare previous candle with current.
    // // final Map<String, LinkedList<CandlestickEvent>> cachedCandlesticks =
    // // marketData.getCachedCandleSticks();
    // // log.info("There is " + marketData.getCheapPairs("USDT").size() + " in
    // cheap
    // // pairs.");
    // // // listen candlestick events of each of pairs that have low price.
    // // return
    // //
    // binanceApiWebSocketClient.onCandlestickEvent(marketData.getCheapPairsSymbols("USDT"),
    // // CandlestickInterval.HOURLY, callback -> {
    // // // if cache have queue of candles of previous(!) period:
    // // if (cachedCandlesticks.containsKey(callback.getSymbol()) &&
    // // cachedCandlesticks
    // // .get(callback.getSymbol()).getFirst().getCloseTime() <
    // // callback.getCloseTime()) {

    // // // get oldest candle event to compare with.
    // // CandlestickEvent previousEvent =
    // // cachedCandlesticks.get(callback.getSymbol()).getFirst();

    // // // if time of close of older candle is different from current more then 1
    // // hour,
    // // // it means need to update compared candles by replacing candles in queue.
    // // if (callback.getCloseTime() - previousEvent.getCloseTime() > 3_600_000L) {
    // //
    // // 3_600_000 = 1 hour
    // // log.info(
    // // "Update candlestick for pair " + callback.getSymbol() + new
    // // Date(previousEvent.getCloseTime())
    // // + " replaced to " + new Date(callback.getCloseTime()));
    // // marketData.pushCandlestickEventToMonitoring(callback.getSymbol(),
    // callback);
    // // } else { // else update last candle.
    // // marketData.addCandlestickEventToMonitoring(callback.getSymbol(),
    // callback);
    // // }
    // // } else { // else update candle.
    // // marketData.addCandlestickEventToMonitoring(callback.getSymbol(),
    // callback);
    // // }
    // // });
    // }

    // compare volumes in current and previous candles to find big volume growth.
    public void compareCandlesVolumes() {
        Map<String, List<Candlestick>> cachedCandlesticks = marketData.getCachedCandles();

        cachedCandlesticks.entrySet().stream()
                .filter(entrySet -> Double.parseDouble(entrySet.getValue().get(1).getVolume()) > Double
                        .parseDouble(entrySet.getValue().get(0).getVolume()) * 2.5 // current volume bigger then
                                                                                   // previous.
                        && Double.parseDouble(entrySet.getValue().get(1).getClose()) > Double
                                .parseDouble(entrySet.getValue().get(0).getClose()) * 1.05) // current price bigger the
                                                                                            // previous.
                .forEach(entrySet -> marketData.addPairToTestBuy(entrySet.getKey(),
                        Double.parseDouble(entrySet.getValue().get(1).getClose()))); // add this pairs to buy.

        // Map<String, CandlestickEvent> cachedCandlestickEvents =
        // marketData.getCachedCandleStickEvents();
        // log.info("There is " + cachedCandlestickEvents.size() + " elements in cache
        // of candle sticks.");
        // cachedCandlestickEvents.values().stream()
        // .filter(candleSticksList -> candleSticksList.getCloseTime() <
        // candleSticksList
        // .getCloseTime()
        // && Double.parseDouble(candleSticksList.getVolume()) > Double
        // .parseDouble(candleSticksList.getVolume()) * 2.5)
        // .map(sticksList -> sticksList).forEach(candleStickEvent -> {
        // marketData.addPairToTestBuy(candleStickEvent.getSymbol(),
        // Double.parseDouble(candleStickEvent.getClose()));
        // });

        // Map<String, LinkedList<CandlestickEvent>> cachedCandlesticks =
        // marketData.getCachedCandleSticks();
        // log.info("There is " + cachedCandlesticks.size() + " elements in cache of
        // candle sticks.");
        // cachedCandlesticks.values().stream()
        // .filter(candleSticksList -> candleSticksList.peekFirst().getCloseTime() <
        // candleSticksList.peekLast()
        // .getCloseTime()
        // && Double.parseDouble(candleSticksList.peekLast().getVolume()) > Double
        // .parseDouble(candleSticksList.peekFirst().getVolume()) * 2.5)
        // .map(sticksList -> sticksList.getLast()).forEach(candleStickEvent -> {
        // marketData.addPairToTestBuy(candleStickEvent.getSymbol(),
        // Double.parseDouble(candleStickEvent.getClose()));
        // });
    }

    public void buyGrownAssets() {
        Map<String, Double> pairsToBuy = marketData.getTestMapToBuy();
        if (pairsToBuy.size() > 0) {
            log.info("There is " + pairsToBuy.size() + " elements in test map to buy.");
            log.info("Buy pairs " + pairsToBuy);
            pairsToBuy.entrySet()
                    .forEach(entrySet -> marketData.putOpenedPosition(entrySet.getKey(), entrySet.getValue()));
            pairsToBuy.clear();
        } else {
            log.info("Nothing to buy");
        }
    }

    public void checkOpenedPositions() {
        Map<String, Double> openedPositions = marketData.getOpenedPositions();
        
        if (openedPositions.isEmpty()) {
            return;
        }

        Map<String, Double> positionsToClose = new HashMap<>();

        List<TickerPrice> currentPrices = marketInfo.getLastTickersPrices(marketData.getAvailablePairsSymbolsFormatted(
                new ArrayList<>(openedPositions.keySet()), 0, openedPositions.size() - 1));

        currentPrices.stream().forEach(tickerPrice -> {
            Double currentPrice = Double.parseDouble(tickerPrice.getPrice());
            if (currentPrice > openedPositions.get(tickerPrice.getSymbol())) { // update current price if it growth
                log.info("Price of " + tickerPrice.getSymbol() + " growth and now equals " + currentPrice);
                openedPositions.put(tickerPrice.getSymbol(), currentPrice);
            } else if (openedPositions.get(tickerPrice.getSymbol()) > currentPrice * 0.93) { // close position if price decreased
                positionsToClose.put(tickerPrice.getSymbol(), currentPrice);
            }
        });

        log.info("!!! Prices of this pairs decreased, selling: " + positionsToClose.toString());
        marketData.representClosingPositions(positionsToClose);
    }

}
