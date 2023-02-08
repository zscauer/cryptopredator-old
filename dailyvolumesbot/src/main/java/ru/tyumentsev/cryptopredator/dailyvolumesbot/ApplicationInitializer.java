package ru.tyumentsev.cryptopredator.dailyvolumesbot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@SuppressWarnings("conditionAssign")
public class ApplicationInitializer implements ApplicationRunner {

    final MarketInfo marketInfo;
    final Map<String, TradingStrategy> tradingStrategies;

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.maximalPairPrice}")
    float maximalPairPrice;

    @Override
    public void run(ApplicationArguments args) {
        if (testLaunch) {
            log.warn("Application launched in test mode. Deals functionality disabled.");
        }

        marketInfo.getAvailableTradePairs(tradingAsset);
        marketInfo.fillCheapPairs(tradingAsset, maximalPairPrice);

        Map<String, TradingStrategy> activeStrategies = tradingStrategies.values().stream()
                .filter(TradingStrategy::isEnabled)
                .collect(Collectors.toMap(value -> String.format("%s (id: %s)", value.getName(), value.getId()), value -> value));

        activeStrategies.forEach((name, implementation) -> implementation.prepareData());

        log.info("Application initialization complete. Active strategies: {}.", activeStrategies.keySet());
    }
}
