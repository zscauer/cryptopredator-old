package ru.tyumentsev.binancespotbot.service;

import java.io.Closeable;
import java.io.IOException;

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
import ru.tyumentsev.binancespotbot.strategy.Buy24hPriceChange;
import ru.tyumentsev.binancespotbot.strategy.BuyBigVolumeGrowth;
import ru.tyumentsev.binancespotbot.strategy.BuyOrderBookTrend;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class StrategyRunner {

    @NonFinal
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    
    AccountManager accountManager;
    BuyBigVolumeGrowth buyBigVolumeGrowth;
    BuyOrderBookTrend buyOrderBookTrend;
    Buy24hPriceChange buy24hPriceChange;

    String USDT = "USDT";

    // +++++++++++++++++++++++++++++++ BuyBigVolumeGrowth strategy

    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.fillCheapPairs.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.fillCheapPairs.initialDelay}")
    public void buyBigVolumeGrowth_fillCheapPairs() {
        buyBigVolumeGrowth.fillCheapPairs(USDT);
    }

    @Timed("buySelectedGrownAssets")
    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.buySelectedGrownAssets.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.buySelectedGrownAssets.initialDelay}")
    public void buyBigVolumeGrowth_buySelectedGrownAssets() {
        if (!testLaunch) {
            buyBigVolumeGrowth.updateMonitoredCandles(USDT, CandlestickInterval.FIFTEEN_MINUTES, 2);

            buyBigVolumeGrowth.findGrownAssets();
            buyBigVolumeGrowth.buyGrownAssets(USDT);
        }
    }

    @Scheduled(fixedDelayString = "${strategy.global.initializeUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.initializeUserDataUpdateStream.initialDelay}")
    public void buyBigVolumeGrowth_initializeAliveUserDataUpdateStream() {
        if (!testLaunch) {
            // User data stream are closing by binance after 24 hours of opening.
            accountManager.initializeUserDataUpdateStream();

            Closeable userDataUpdateEventsListener = buyBigVolumeGrowth.getUserDataUpdateEventsListener();
            if (userDataUpdateEventsListener != null) {
                try {
                    userDataUpdateEventsListener.close();
                } catch (IOException e) {
                    log.error("Error while trying to close user data update events listener:\n{}.", e.getMessage());
                    e.printStackTrace();
                }
            }

            buyBigVolumeGrowth.monitorUserDataUpdateEvents();
        }
    }

    @Scheduled(fixedDelayString = "${strategy.global.keepAliveUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.keepAliveUserDataUpdateStream.initialDelay}")
    public void buyBigVolumeGrowth_keepAliveUserDataUpdateStream() {
        if (!testLaunch) {
            accountManager.keepAliveUserDataUpdateStream();
        }
    }
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


}
