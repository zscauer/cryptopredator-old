package ru.tyumentsev.binancespotbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.market.CandlestickInterval;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.binancespotbot.strategy.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class StrategyRunner {

    VolumeCatcher volumeCatcher;
    BuyOrderBookTrend buyOrderBookTrend;
    Buy24hPriceChange buy24hPriceChange;
    BearCub bearCub;

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

    // +++++++++++++++++++++++++++++++ Buy24hPriceChange strategy

    // launch every 12h
    //
//    @Scheduled(fixedDelayString = "${strategy.buy24hPriceChange.fillCheapPairs.fixedDelay}", initialDelayString = "${strategy.buy24hPriceChange.fillCheapPairs.initialDelay}")
//    public void buy24hPriceChange_fillCheapPairs() {
//        buy24hPriceChange.fillCheapPairs(tradingAsset);
//    }

    @Scheduled(fixedDelayString = "${strategy.buy24hPriceChange.defineGrowingPairs.fixedDelay}", initialDelayString = "${strategy.buy24hPriceChange.defineGrowingPairs.initialDelay}")
    public void buy24hPriceChange_defineGrowingPairs() {
        if (buy24hPriceChangeEnabled && !testLaunch) {
            buy24hPriceChange.defineGrowingPairs(tradingAsset);
//        buy24hPriceChange.fillWebSocketStreams();
        }
    }

    // ------------------------------- Buy24hPriceChange strategy

    // +++++++++++++++++++++++++++++++ BearCub strategy

//    @Scheduled(fixedDelayString = "${strategy.bearCub.defineGrowingPairs.fixedDelay}", initialDelayString = "${strategy.bearCub.defineGrowingPairs.initialDelay}")
//    public void bearCub_defineGrowingPairs() {
//        bearCub.defineGrowingPairs(tradingAsset);
//        bearCub.openShortsForGrownPairs();
//    }

    // ------------------------------- BearCub strategy

}
