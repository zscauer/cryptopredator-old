package ru.tyumentsev.binancetestbot.strategy;

import java.io.Closeable;
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

        List<String> availablePairs = marketData.getAvailablePairs("USDT");
        List<String> filteredPairs = marketInfo
                .getLastTickersPrices(
                        marketData.getAvailablePairsSymbolsFormatted(availablePairs, 0, availablePairs.size() - 1))
                .stream().filter(tickerPrice -> Double.parseDouble(tickerPrice.getPrice()) < 2)
                .map(TickerPrice::getSymbol).toList();
        System.out.println("Filtered " + filteredPairs.size() + " cheap tickers.");

        marketData.setCheapPairs(filteredPairs);

        // return filteredPairs;
    }

    public Closeable updateMonitoredCandleSticks() {
        Map<String, CandlestickEvent> monitoredCandlesticks = marketData.getMonitoredCandleSticks();

        return binanceApiWebSocketClient.onCandlestickEvent(marketData.getAvailablePairsSymbols("USDT"),

                CandlestickInterval.HOURLY, callback -> {
                    if (monitoredCandlesticks.containsKey(callback.getSymbol()) && monitoredCandlesticks
                            .get(callback.getSymbol()).getCloseTime() < callback.getCloseTime()) {
                        CandlestickEvent previuosEvent = monitoredCandlesticks.get(callback.getSymbol());
                        if (callback.getCloseTime() - previuosEvent.getCloseTime() > 3_600_000L) { // 3_600_000 = 1 hour
                            log.info(
                                    "Update candlestick for pair " + callback.getSymbol() + previuosEvent.getCloseTime()
                                            + " replaced to " + callback.getCloseTime());
                            marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
                        }
                        if (Double.parseDouble(callback.getVolume()) > Double.parseDouble(previuosEvent.getVolume()) * 2
                                && Double.parseDouble(
                                        callback.getClose()) > Double.parseDouble(previuosEvent.getClose()) * 1.05) {
                            System.out.println("Found growth volumes and price in " + callback.getSymbol());
                            marketData.addPairToTestBuy(callback.getSymbol(), Double.parseDouble(callback.getClose()));
                        }
                    } else {
                        marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
                    }
                });
    }

    public void buyGrownAssets() {
        Map<String, Double> pairsToBuy = marketData.getTestMapToBuy();
        if (pairsToBuy.size() > 0) {
            log.info("Buy pairs " + pairsToBuy);
            pairsToBuy.clear();
        } else {
            log.info("Nothing to buy");
        }

    }
}
