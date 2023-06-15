package ru.tyumentsev.cryptopredator.indicatorvirginbot.configuration;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Accessors(fluent = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GlobalStrategyConfiguration {

    boolean testLaunch;
    String tradingAsset;
    int minimalAssetBalance;
    int baseOrderVolume;

    public GlobalStrategyConfiguration(@Value("${strategy.global.tradingAsset}") String tradingAsset,
                                       @Value("${strategy.global.minimalAssetBalance}") int minimalAssetBalance,
                                       @Value("${strategy.global.baseOrderVolume}") int baseOrderVolume,
                                       @Value("${applicationconfig.testLaunch}") boolean testLaunch) {
        this.testLaunch = testLaunch;
        this.tradingAsset = tradingAsset;
        this.minimalAssetBalance = minimalAssetBalance;
        this.baseOrderVolume = baseOrderVolume;
    }
}
