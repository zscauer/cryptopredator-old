package ru.tyumentsev.cryptopredator.binancespotbot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.binancespotbot.cache.MarketData;
import ru.tyumentsev.cryptopredator.binancespotbot.service.AccountManager;
import ru.tyumentsev.cryptopredator.binancespotbot.service.DataService;
import ru.tyumentsev.cryptopredator.binancespotbot.service.MarketInfo;
import ru.tyumentsev.cryptopredator.binancespotbot.strategy.TradingStrategy;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitializer implements ApplicationRunner {

    MarketData marketData;
    MarketInfo marketInfo;
    AccountManager accountManager;
    Map<String, TradingStrategy> tradingStrategies;
    DataService dataService;

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
            LocalDateTime sellTime = LocalDateTime.now();
        }

        marketData.addAvailablePairs(tradingAsset, marketInfo.getAvailableTradePairs(tradingAsset));
        marketData.initializeOpenedLongPositionsFromMarket(marketInfo, accountManager);
        marketData.fillCheapPairs(tradingAsset, marketInfo);

        Map<String, TradingStrategy> activeStrategies = tradingStrategies.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        activeStrategies.forEach((name, implementation) -> implementation.prepareData());

        log.info("Application initialization complete.\nActive strategies: {}.", activeStrategies.keySet());
    }
}
