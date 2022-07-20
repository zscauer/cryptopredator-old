package ru.tyumentsev.binancetestbot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.service.MarketInfo;
import ru.tyumentsev.binancetestbot.strategy.BuyBigVolumeGrowth;

@Component
public class ApplicationInitializer implements ApplicationRunner {

    @Autowired
    MarketData marketData;
    @Autowired
    MarketInfo marketInfo;
    @Autowired
    BuyBigVolumeGrowth buyBigVolumeGrowth;

    final String USDT = "USDT";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // TODO change to get assets tickers from config file.
        marketData.addAvailablePairs(USDT, marketInfo.getAvailableTradePairs(USDT));
        buyBigVolumeGrowth.initializeMarketData();
    }
}
