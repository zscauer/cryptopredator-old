package ru.tyumentsev.binancespotbot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.service.MarketInfo;
import ru.tyumentsev.binancespotbot.strategy.BuyBigVolumeGrowth;

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
        marketData.initializeOpenedPositionsFromMarket(marketInfo, buyBigVolumeGrowth.getAccountManager());
    }
}
