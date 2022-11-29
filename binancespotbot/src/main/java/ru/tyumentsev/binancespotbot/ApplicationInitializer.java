package ru.tyumentsev.binancespotbot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.service.MarketInfo;
import ru.tyumentsev.binancespotbot.strategy.BuyBigVolumeGrowth;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitializer implements ApplicationRunner {

    @NonFinal
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    
    MarketData marketData;
    MarketInfo marketInfo;
    BuyBigVolumeGrowth buyBigVolumeGrowth;

    String USDT = "USDT";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (testLaunch) {
            log.warn("Application launched in test mode. Deals functionality disabled.");
        }
        // TODO change to get assets tickers from config file.
        marketData.addAvailablePairs(USDT, marketInfo.getAvailableTradePairs(USDT));
        marketData.initializeOpenedPositionsFromMarket(marketInfo, buyBigVolumeGrowth.getAccountManager());
        log.info("Application initialization complete. Logger class is: '{}'.", log.getClass());
    }
}
