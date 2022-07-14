package ru.tyumentsev.binancetestbot;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.binance.api.client.domain.market.CandlestickInterval;

import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.service.MarketInfo;

@Component
public class ApplicationInitializer implements ApplicationRunner {

    @Autowired
    MarketData marketData;
    @Autowired
    MarketInfo marketInfo;

    final String USDT = "USDT";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // TODO change to get assets tickers from config file.
        marketData.addAvailablePairs(USDT, marketInfo.getAvailableTradePairs(USDT));
        System.out.println(marketData.getAvailablePairsSymbols(USDT));
        
        marketData.fillMonitoredTicks(
                marketInfo.getCandleSticks(marketData.getAvailablePairs("USDT"), CandlestickInterval.HOURLY, 2));
        System.out.println(marketData.getMonitoredCandleTicks());

    //     List<String> list = new ArrayList<>();
    //     list.add("LEVERUSDT");
    //     marketData.fillMonitoredTicks(
    //         marketInfo.getCandleSticks(list, CandlestickInterval.HOURLY, 2));
    // System.out.println(marketData.getMonitoredCandleTicks());
    }

}
