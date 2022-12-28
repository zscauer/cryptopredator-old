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
import ru.tyumentsev.binancespotbot.service.AccountManager;
import ru.tyumentsev.binancespotbot.service.MarketInfo;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitializer implements ApplicationRunner {

    MarketData marketData;
    MarketInfo marketInfo;
    AccountManager accountManager;

    @NonFinal
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @NonFinal
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (testLaunch) {
            log.warn("Application launched in test mode. Deals functionality disabled.");
        }
        // TODO change to get assets tickers from config file.
        marketData.addAvailablePairs(tradingAsset, marketInfo.getAvailableTradePairs(tradingAsset));
        marketData.initializeOpenedLongPositionsFromMarket(marketInfo, accountManager);
        marketData.fillCheapPairs(tradingAsset, marketInfo);
        marketData.constructCandleStickEventsCache(tradingAsset);
        log.info("Application initialization complete. Logger class is: '{}'.", log.getClass());
    }
}
