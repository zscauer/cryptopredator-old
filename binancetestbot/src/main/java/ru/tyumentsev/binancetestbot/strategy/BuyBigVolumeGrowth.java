package ru.tyumentsev.binancetestbot.strategy;

import java.io.Closeable;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.CandlestickEvent;
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

    public void fillCheapPairs() {

        // get all pairs, that trades against USDT.
        List<String> availablePairs = marketData.getAvailablePairs("USDT");
        // filter available pairs to get cheaper then 1 USDT.
        List<String> filteredPairs = marketInfo
                .getLastTickersPrices(
                        marketData.getAvailablePairsSymbolsFormatted(availablePairs, 0, availablePairs.size() - 1))
                .stream().filter(tickerPrice -> Double.parseDouble(tickerPrice.getPrice()) < 1)
                .map(TickerPrice::getSymbol).toList();
        log.info("Filtered " + filteredPairs.size() + " cheap tickers.");

        // place filtered pairs to cache.
        marketData.putCheapPairs("USDT", filteredPairs);
    }

    public List<String> getCheapPairs(String asset) {
        return marketData.getCheapPairs(asset);
    }

    public Closeable updateMonitoredCandleSticks(List<String> pairs) {
        // get candlesticks cache to compare previous candle with current.
        final Map<String, CandlestickEvent> cachedCandlesticks = marketData.getCachedCandleSticks();
        log.info("There is " + marketData.getCheapPairs("USDT").size() + " in cheap pairs.");

        // listen candlestick events of each of pairs that have low price.
        return binanceApiWebSocketClient.onCandlestickEvent(marketData.getCheapPairsSymbols(pairs),
                CandlestickInterval.HOURLY, callback -> {
                    // // if cache have queue of candles of previous(!) period:
                    // if (cachedCandlesticks.containsKey(callback.getSymbol())) {
                    //     // get oldest candle event to compare with.
                    //     CandlestickEvent previousEvent = cachedCandlesticks.get(callback.getSymbol());
                        
                    //     if (callback.getCloseTime().equals(previousEvent.getCloseTime())) {
                    //         marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
                    //     }

                    //     // // if time of close of older candle is different from current more then 1 hour,
                    //     // // it means need to update compared candles by replacing candles in queue.
                    //     // else if (callback.getCloseTime() - previousEvent.getCloseTime() > 3_600_000L) { // 3_600_000 = 1
                    //     //                                                                                 // hour
                    //     //     log.info(
                    //     //             "Update candlestick for pair " + callback.getSymbol()
                    //     //                     + new Date(previousEvent.getCloseTime())
                    //     //                     + " replaced to " + new Date(callback.getCloseTime()));
                    //     //     marketData.pushCandlestickEventToMonitoring(callback.getSymbol(), callback);
                    //     // } else { // else update last candle.
                    //     //     marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
                    //     // }
                    // } else { // else update candle.
                    //     marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
                    // }
                    marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
                });
        // // get candlesticks cache to compare previous candle with current.
        // final Map<String, LinkedList<CandlestickEvent>> cachedCandlesticks =
        // marketData.getCachedCandleSticks();
        // log.info("There is " + marketData.getCheapPairs("USDT").size() + " in cheap
        // pairs.");
        // // listen candlestick events of each of pairs that have low price.
        // return
        // binanceApiWebSocketClient.onCandlestickEvent(marketData.getCheapPairsSymbols("USDT"),
        // CandlestickInterval.HOURLY, callback -> {
        // // if cache have queue of candles of previous(!) period:
        // if (cachedCandlesticks.containsKey(callback.getSymbol()) &&
        // cachedCandlesticks
        // .get(callback.getSymbol()).getFirst().getCloseTime() <
        // callback.getCloseTime()) {

        // // get oldest candle event to compare with.
        // CandlestickEvent previousEvent =
        // cachedCandlesticks.get(callback.getSymbol()).getFirst();

        // // if time of close of older candle is different from current more then 1
        // hour,
        // // it means need to update compared candles by replacing candles in queue.
        // if (callback.getCloseTime() - previousEvent.getCloseTime() > 3_600_000L) { //
        // 3_600_000 = 1 hour
        // log.info(
        // "Update candlestick for pair " + callback.getSymbol() + new
        // Date(previousEvent.getCloseTime())
        // + " replaced to " + new Date(callback.getCloseTime()));
        // marketData.pushCandlestickEventToMonitoring(callback.getSymbol(), callback);
        // } else { // else update last candle.
        // marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
        // }
        // } else { // else update candle.
        // marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
        // }
        // });
    }

    // compare volumes in current and previous candles to find big volume growth.
    public void compareCandlesVolumes() {
        Map<String, CandlestickEvent> cachedCandlesticks = marketData.getCachedCandleSticks();
        log.info("There is " + cachedCandlesticks.size() + " elements in cache of candle sticks.");
        cachedCandlesticks.values().stream()
                .filter(candleSticksList -> candleSticksList.getCloseTime() < candleSticksList
                        .getCloseTime()
                        && Double.parseDouble(candleSticksList.getVolume()) > Double
                                .parseDouble(candleSticksList.getVolume()) * 2.5)
                .map(sticksList -> sticksList).forEach(candleStickEvent -> {
                    marketData.addPairToTestBuy(candleStickEvent.getSymbol(),
                            Double.parseDouble(candleStickEvent.getClose()));
                });
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

    // public Closeable updateMonitoredCandleSticks() {
    // // get candlestick cache to compare previous candle with current.
    // Map<String, CandlestickEvent> cachedCandlesticks =
    // marketData.getCachedCandleSticks();

    // // listen candlestick events of each of pairs that have low price.
    // return
    // binanceApiWebSocketClient.onCandlestickEvent(marketData.getCheapPairsSymbols("USDT"),
    // CandlestickInterval.HOURLY, callback -> {
    // // if cache have candle of previous(!) period:
    // if (cachedCandlesticks.containsKey(callback.getSymbol()) &&
    // cachedCandlesticks
    // .get(callback.getSymbol()).getCloseTime() < callback.getCloseTime()) {

    // // get previous candle event to compare with.
    // CandlestickEvent previousEvent =
    // cachedCandlesticks.get(callback.getSymbol());

    // // if time of close is defferent more then 1 hour, it means that
    // if (callback.getCloseTime() - previousEvent.getCloseTime() > 3_600_000L) { //
    // 3_600_000 = 1 hour
    // log.info(
    // "Update candlestick for pair " + callback.getSymbol() +
    // previousEvent.getCloseTime()
    // + " replaced to " + callback.getCloseTime());
    // marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
    // }
    // if (Double.parseDouble(callback.getVolume()) >
    // Double.parseDouble(previousEvent.getVolume()) * 2
    // && Double.parseDouble(
    // callback.getClose()) > Double.parseDouble(previousEvent.getClose()) * 1.05) {
    // System.out.println("Found growth volumes and price in " +
    // callback.getSymbol());
    // marketData.addPairToTestBuy(callback.getSymbol(),
    // Double.parseDouble(callback.getClose()));
    // }
    // } else { // else update candle.
    // marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
    // }
    // });
    // }

    public void buyGrownAssets() {
        Map<String, Double> pairsToBuy = marketData.getTestMapToBuy();
        if (pairsToBuy.size() > 0) {
            log.info("There is " + pairsToBuy.size() + " elements in test map to buy.");
            marketData.getCheapPairs("USDT").removeAll(pairsToBuy.keySet());
            log.info("Buy pairs " + pairsToBuy);
            pairsToBuy.clear();
        } else {
            log.info("Nothing to buy");
        }

    }
}
