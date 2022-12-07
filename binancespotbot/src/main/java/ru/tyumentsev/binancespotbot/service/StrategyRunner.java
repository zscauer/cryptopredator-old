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

    String USDT = "USDT";

    // +++++++++++++++++++++++++++++++ BuyFastGrowth strategy

    // @Scheduled(fixedDelayString =
    // "${strategy.buyFastGrowth.collectPairsToBuy.fixedDelay}", initialDelayString
    // = "${strategy.buyFastGrowth.collectPairsToBuy.initialDelay}")
    // private void buyFastGrowth_collectPairsToBuy() {
    // System.out.println("Collecting pairs runs");
    // log.info("Test logging info");
    // log.info("Found next pairs to buy:");
    // buyFastGrowth.addPairsToBuy("USDT").stream().forEach(element -> {
    // log.info(element.getSymbol() + " changed to " +
    // element.getPriceChangePercent() + "%");
    // });
    // }

    // @Scheduled(fixedDelayString =
    // "${strategy.buyFastGrowth.buyCollectedPairs.fixedDelay}", initialDelayString
    // = "${strategy.buyFastGrowth.buyCollectedPairs.initialDelay}")
    // private void buyFastGrowth_buyCollectedPairs() {
    // log.info("Buying collected pairs runs");
    // buyFastGrowth.makeOrdersForSelectedPairsToBuy();
    // }

    // @Scheduled(fixedDelayString =
    // "${strategy.buyFastGrowth.closeOpenedPositions.fixedDelay}",
    // initialDelayString =
    // "${strategy.buyFastGrowth.closeOpenedPositions.initialDelay}")
    // private void buyFastGrowth_closeOpenedPositions() {
    // log.info("Closing opened positions runs");
    // buyFastGrowth.closeOpenedPositions();
    // }

    // ------------------------------- BuyFastGrowth strategy

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

    @Timed("checkOpenedPositions")
    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.checkOpenedPositions.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.checkOpenedPositions.initialDelay}")
    public void buyBigVolumeGrowth_checkOpenedPositions() {
        if (!testLaunch) {
            buyBigVolumeGrowth.checkMarketPositions(USDT);
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

    // @Scheduled(fixedDelay = 300000L, initialDelay = 20000L)
    // public void buyOrderBookTrend() {
    //   buyOrderBookTrend.testMethod1();
        
    // }


    // ------------------------------- BuyOrderBookTrend strategy

}
