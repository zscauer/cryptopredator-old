package ru.tyumentsev.binancetestbot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.binance.api.client.BinanceApiWebSocketClient;

import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.service.MarketInfo;

@Component
public class ApplicationInitializer implements ApplicationRunner {

    @Autowired
    MarketData marketData;
    @Autowired
    MarketInfo marketInfo;
    @Autowired
    BinanceApiWebSocketClient binanceApiWebSocketClient;

    final String USDT = "USDT";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // TODO change to get assets tickers from config file.
        marketData.addAvailablePairs(USDT, marketInfo.getAvailableTradePairs(USDT));
        // System.out.println(marketData.getAvailablePairsSymbols(USDT));

        // + TEST CODE --------------------

        // Map<String, CandlestickEvent> monitoredCandlesticks = marketData.getMonitoredCandleSticks();

        // Closeable ws = binanceApiWebSocketClient.onCandlestickEvent("ethusdt, btcusdt, leverusdt, vgxusdt", CandlestickInterval.HOURLY, callback -> {
        //     // marketData.addCandlestickEventToMonitoring(callback);
        //     if (monitoredCandlesticks.containsKey(callback.getSymbol()) && monitoredCandlesticks.get(callback.getSymbol()).getCloseTime() < callback.getCloseTime()) {
        //         System.out.println("New candle catched");
        //     } else {
        //         System.out.println("Candle not in collection");
        //         marketData.addCandlestickEventToMonitoring(callback.getSymbol(), callback);
        //         // System.out.println(monitoredCandlesticks);
        //         // System.out.println(callback.getSymbol());
        //     }
        // });
        // Thread.sleep(5000);
        // ws.close();
        // System.out.println(monitoredCandlesticks);
        
        // - TEST CODE --------------------
    }
}
