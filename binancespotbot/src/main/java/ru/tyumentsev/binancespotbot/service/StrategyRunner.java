package ru.tyumentsev.binancespotbot.service;

import java.io.Closeable;
import java.io.IOException;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.market.CandlestickInterval;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancespotbot.strategy.BuyBigVolumeGrowth;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Log4j2
public class StrategyRunner {

    AccountManager accountManager;
    BuyBigVolumeGrowth buyBigVolumeGrowth;

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
    private void buyBigVolumeGrowth_fillCheapPairs() {
        // log.info("Fill cheap pairs from strategy runner.");
        buyBigVolumeGrowth.fillCheapPairs(USDT);
    }

    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.buySelectedGrownAssets.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.buySelectedGrownAssets.initialDelay}")
    private void buyBigVolumeGrowth_buySelectedGrownAssets() {
        // log.info("Find big volume growth from strategy runner.");
        buyBigVolumeGrowth.updateMonitoredCandles(USDT, CandlestickInterval.FIFTEEN_MINUTES, 2);

        // log.info("Monitored candles size is: " +
        // marketData.getCachedCandles().size());
        buyBigVolumeGrowth.findGrownAssets();
        buyBigVolumeGrowth.buyGrownAssets(USDT);
    }

    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.checkOpenedPositions.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.checkOpenedPositions.initialDelay}")
    private void buyBigVolumeGrowth_checkOpenedPositions() {
        // log.info("Check opened positions from strategy runner.");
        buyBigVolumeGrowth.checkMarketPositions(USDT);
    }

    @Scheduled(fixedDelayString = "${strategy.global.initializeUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.initializeUserDataUpdateStream.initialDelay}")
    private void buyBigVolumeGrowth_initializeAliveUserDataUpdateStream() {
        // User data stream are closing by binance after 24 hours of starting.
        // log.info("Sending signal to initialize user data update stream.");
        accountManager.initializeUserDataUpdateStream();

        Closeable userDataUpdateEventsListener = buyBigVolumeGrowth.getUserDataUpdateEventsListener();
        if (!(userDataUpdateEventsListener == null)) {
            try {
                userDataUpdateEventsListener.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        buyBigVolumeGrowth.monitorUserDataUpdateEvents();
    }

    @Scheduled(fixedDelayString = "${strategy.global.keepAliveUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.keepAliveUserDataUpdateStream.initialDelay}")
    private void buyBigVolumeGrowth_keepAliveUserDataUpdateStream() {
        // log.info("Sending signal to keep alive user data update stream.");
        accountManager.keepAliveUserDataUpdateStream();
    }
    // ------------------------------- BuyBigVolumeGrowth strategy
}
