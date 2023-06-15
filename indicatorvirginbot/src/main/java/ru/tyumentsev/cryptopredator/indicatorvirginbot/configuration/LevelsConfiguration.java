package ru.tyumentsev.cryptopredator.indicatorvirginbot.configuration;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Accessors(fluent = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LevelsConfiguration {

    boolean testLaunch;
    String tradingAsset;
    int minimalAssetBalance;
    int baseOrderVolume;

    boolean levelsEnabled;
    @NonFinal
    @Setter
    boolean followBtcTrend;
    int ordersQtyLimit;
    boolean averagingEnabled;
    float priceDecreaseFactor;
    float pairTakeProfitFactor;
    float takeProfitPriceDecreaseFactor;
    float averagingTrigger;

    public static String STRATEGY_NAME = "levels";
    public static Integer STRATEGY_ID = 1022;
    public static String USER_DATA_UPDATE_ENDPOINT = String.format("http://indicatorvirginbot:8080/%s/userDataUpdateEvent", STRATEGY_NAME);

    public LevelsConfiguration(GlobalStrategyConfiguration globalStrategyConfiguration,
                               @Value("${strategy.levels.enabled}") boolean levelsEnabled,
                               @Value("${strategy.levels.followBtcTrend}") boolean followBtcTrend,
                               @Value("${strategy.levels.ordersQtyLimit}") int ordersQtyLimit,
                               @Value("${strategy.levels.averagingEnabled}") boolean averagingEnabled,
                               @Value("${strategy.levels.priceDecreaseFactor}") float priceDecreaseFactor,
                               @Value("${strategy.levels.pairTakeProfitFactor}") float pairTakeProfitFactor,
                               @Value("${strategy.levels.takeProfitPriceDecreaseFactor}") float takeProfitPriceDecreaseFactor,
                               @Value("${strategy.levels.averagingTrigger}") float averagingTrigger) {
        this.testLaunch = globalStrategyConfiguration.testLaunch();
        this.tradingAsset = globalStrategyConfiguration.tradingAsset();
        this.minimalAssetBalance = globalStrategyConfiguration.minimalAssetBalance();
        this.baseOrderVolume = globalStrategyConfiguration.baseOrderVolume();
        this.levelsEnabled = levelsEnabled;
        this.followBtcTrend = followBtcTrend;
        this.ordersQtyLimit = ordersQtyLimit;
        this.averagingEnabled = averagingEnabled;
        this.priceDecreaseFactor = priceDecreaseFactor;
        this.pairTakeProfitFactor = pairTakeProfitFactor;
        this.takeProfitPriceDecreaseFactor = takeProfitPriceDecreaseFactor;
        this.averagingTrigger = averagingTrigger;
    }
}
