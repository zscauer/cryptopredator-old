package ru.tyumentsev.cryptopredator.binancespotbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.market.CandlestickInterval;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.binancespotbot.strategy.BearCub;
import ru.tyumentsev.cryptopredator.binancespotbot.strategy.BuyOrderBookTrend;
import ru.tyumentsev.cryptopredator.binancespotbot.strategy.Daily;
import ru.tyumentsev.cryptopredator.binancespotbot.strategy.VolumeCatcher;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class StrategyRunner {

    VolumeCatcher volumeCatcher;
    BuyOrderBookTrend buyOrderBookTrend;
    BearCub bearCub;
    Daily daily;

    @NonFinal
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @NonFinal
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @NonFinal
    @Value("${strategy.volumeCatcher.enabled}")
    boolean volumeCatcherEnabled;
    @NonFinal
    @Value("${strategy.daily.enabled}")
    boolean dailyEnabled;
    @NonFinal
    @Value("${strategy.buy24hPriceChange.enabled}")
    boolean buy24hPriceChangeEnabled;


    // +++++++++++++++++++++++++++++++ VolumeCatcher strategy

    @Scheduled(fixedDelayString = "${strategy.volumeCatcher.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.volumeCatcher.startCandlstickEventsCacheUpdating.initialDelay}")
    public void volumeCatcher_startCandlstickEventsCacheUpdating() {
        if (volumeCatcherEnabled && !testLaunch) {
             volumeCatcher.startCandlstickEventsCacheUpdating(tradingAsset, CandlestickInterval.FIVE_MINUTES);
        }
    }

    // ------------------------------- VolumeCatcher strategy

    // +++++++++++++++++++++++++++++++ BuyOrderBookTrend strategy

//     @Scheduled(fixedDelay = 300000L, initialDelay = 20000L)
//     public void buyOrderBookTrend_generateWebSocketStreams() {
//       buyOrderBookTrend.generateWebSocketStreams();
//     }
//
//    @Scheduled(fixedDelay = 180000L, initialDelay = 60000L)
//    public void buyOrderBookTrend_analizeInterest() {
//        buyOrderBookTrend.analizeInterest();
//    }

    // ------------------------------- BuyOrderBookTrend strategy

    // +++++++++++++++++++++++++++++++ BearCub strategy

//    @Scheduled(fixedDelayString = "${strategy.bearCub.defineGrowingPairs.fixedDelay}", initialDelayString = "${strategy.bearCub.defineGrowingPairs.initialDelay}")
//    public void bearCub_defineGrowingPairs() {
//        bearCub.defineGrowingPairs(tradingAsset);
//        bearCub.openShortsForGrownPairs();
//    }

    // ------------------------------- BearCub strategy

    // +++++++++++++++++++++++++++++++ daily strategy

    @Scheduled(fixedDelayString = "${strategy.volumeCatcher.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.volumeCatcher.startCandlstickEventsCacheUpdating.initialDelay}")
    public void daily_startCandlstickEventsCacheUpdating() {
        if (dailyEnabled && !testLaunch) {
            daily.startCandlstickEventsCacheUpdating(tradingAsset, CandlestickInterval.DAILY);
        }
    }

    // ------------------------------- daily strategy

}
