package ru.tyumentsev.binancespotbot.service;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.market.CandlestickInterval;

import io.micrometer.core.annotation.Timed;
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

    AccountManager accountManager;
    BuyBigVolumeGrowth buyBigVolumeGrowth;
    BuyOrderBookTrend buyOrderBookTrend;
    Buy24hPriceChange buy24hPriceChange;
    BearCub bearCub;

    String USDT = "USDT";

    @NonFinal
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @NonFinal
    @Value("${strategy.buyBigVolumeGrowth.enabled}")
    boolean buyBigVolumeGrowthEnabled;


    // +++++++++++++++++++++++++++++++ BuyBigVolumeGrowth strategy

//    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.fillCheapPairs.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.fillCheapPairs.initialDelay}")
//    public void buyBigVolumeGrowth_fillCheapPairs() {
//        buyBigVolumeGrowth.fillCheapPairs(USDT);
//    }

    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.startCandlstickEventsCacheUpdating.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.startCandlstickEventsCacheUpdating.initialDelay}")
    public void buyBigVolumeGrowth_startCandlstickEventsCacheUpdating() {
        if (buyBigVolumeGrowthEnabled) {
             buyBigVolumeGrowth.startCandlstickEventsCacheUpdating(USDT, CandlestickInterval.FIFTEEN_MINUTES);
        }
    }

    // +++ buy fast testing
//    @Timed("buySelectedGrownAssets")
//    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.buySelectedGrownAssets.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.buySelectedGrownAssets.initialDelay}")
//    public void buyBigVolumeGrowth_buySelectedGrownAssets() {
//        if (buyBigVolumeGrowthEnabled && !testLaunch) {
////            buyBigVolumeGrowth.updateMonitoredCandles(USDT, CandlestickInterval.FIFTEEN_MINUTES, 2);
//
////            buyBigVolumeGrowth.findGrownAssets();
//            buyBigVolumeGrowth.buyGrownAssets(USDT);
//        }
//    }
    // --- buy fast testing

//    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.stopMonitorOpenedLongPositions.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.stopMonitorOpenedLongPositions.initialDelay}")
//    public void buyBigVolumeGrowth_stopMonitorOpenedLongPositions() {
//        if (buyBigVolumeGrowthEnabled && !testLaunch) {
//            buyBigVolumeGrowth.stopMonitorOpenedLongPositions();
//        }
//    }

    // ------------------------------- BuyBigVolumeGrowth strategy

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
//        buy24hPriceChange.fillCheapPairs(USDT);
//    }

//    @Scheduled(fixedDelayString = "${strategy.buy24hPriceChange.defineGrowingPairs.fixedDelay}", initialDelayString = "${strategy.buy24hPriceChange.defineGrowingPairs.initialDelay}")
//    public void buy24hPriceChange_defineGrowingPairs() {
//        buy24hPriceChange.defineGrowingPairs(USDT);
////        buy24hPriceChange.fillWebSocketStreams();
//    }

    // ------------------------------- Buy24hPriceChange strategy

    // +++++++++++++++++++++++++++++++ BearCub strategy

//    @Scheduled(fixedDelayString = "${strategy.bearCub.defineGrowingPairs.fixedDelay}", initialDelayString = "${strategy.bearCub.defineGrowingPairs.initialDelay}")
//    public void bearCub_defineGrowingPairs() {
//        bearCub.defineGrowingPairs(USDT);
//        bearCub.openShortsForGrownPairs();
//    }

    // ------------------------------- BearCub strategy

}
