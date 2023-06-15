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
public class IndicatorVirginConfiguration {

    boolean testLaunch;
    String tradingAsset;
    int minimalAssetBalance;
    int baseOrderVolume;

    float priceDecreaseFactor;
    float pairTakeProfitFactor;
    float takeProfitPriceDecreaseFactor;
    float averagingTrigger;

    boolean indicatorVirginEnabled;
    @NonFinal
    @Setter
    boolean followBtcTrend;
    int ordersQtyLimit;
    boolean averagingEnabled;

    public static String STRATEGY_NAME = "indicatorvirgin";
    public static Integer STRATEGY_ID = 1021;
    public static String USER_DATA_UPDATE_ENDPOINT = String.format("http://indicatorvirginbot:8080/%s/userDataUpdateEvent", STRATEGY_NAME);

    public IndicatorVirginConfiguration(GlobalStrategyConfiguration globalStrategyConfiguration,
                                        @Value("${strategy.indicatorVirgin.priceDecreaseFactor}") float priceDecreaseFactor,
                                        @Value("${strategy.indicatorVirgin.pairTakeProfitFactor}") float pairTakeProfitFactor,
                                        @Value("${strategy.indicatorVirgin.takeProfitPriceDecreaseFactor}") float takeProfitPriceDecreaseFactor,
                                        @Value("${strategy.indicatorVirgin.averagingTrigger}") float averagingTrigger,
                                        @Value("${strategy.indicatorVirgin.enabled}") boolean indicatorVirginEnabled,
                                        @Value("${strategy.indicatorVirgin.followBtcTrend}") boolean followBtcTrend,
                                        @Value("${strategy.indicatorVirgin.ordersQtyLimit}") int ordersQtyLimit,
                                        @Value("${strategy.indicatorVirgin.averagingEnabled}") boolean averagingEnabled) {
        this.testLaunch = globalStrategyConfiguration.testLaunch();
        this.tradingAsset = globalStrategyConfiguration.tradingAsset();
        this.minimalAssetBalance = globalStrategyConfiguration.minimalAssetBalance();
        this.baseOrderVolume = globalStrategyConfiguration.baseOrderVolume();
        this.priceDecreaseFactor = priceDecreaseFactor;
        this.pairTakeProfitFactor = pairTakeProfitFactor;
        this.takeProfitPriceDecreaseFactor = takeProfitPriceDecreaseFactor;
        this.averagingTrigger = averagingTrigger;
        this.indicatorVirginEnabled = indicatorVirginEnabled;
        this.followBtcTrend = followBtcTrend;
        this.ordersQtyLimit = ordersQtyLimit;
        this.averagingEnabled = averagingEnabled;
    }
}
