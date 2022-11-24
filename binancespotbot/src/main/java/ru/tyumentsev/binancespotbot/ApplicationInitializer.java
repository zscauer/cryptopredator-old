package ru.tyumentsev.binancespotbot;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.service.MarketInfo;
import ru.tyumentsev.binancespotbot.strategy.BuyBigVolumeGrowth;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ApplicationInitializer implements ApplicationRunner {

    MarketData marketData;
    MarketInfo marketInfo;
    BuyBigVolumeGrowth buyBigVolumeGrowth;

    String USDT = "USDT";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // TODO change to get assets tickers from config file.
        marketData.addAvailablePairs(USDT, marketInfo.getAvailableTradePairs(USDT));
        marketData.initializeOpenedPositionsFromMarket(marketInfo, buyBigVolumeGrowth.getAccountManager());
    }
}
