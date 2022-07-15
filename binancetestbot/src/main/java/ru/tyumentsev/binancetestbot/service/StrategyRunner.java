package ru.tyumentsev.binancetestbot.service;

import java.io.Closeable;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancetestbot.strategy.BuyBigVolumeGrowth;
import ru.tyumentsev.binancetestbot.strategy.BuyFastGrowth;

@Service
@Log4j2
public class StrategyRunner {

    @Autowired
    BuyFastGrowth buyFastGrowth;
    @Autowired
    BuyBigVolumeGrowth buyBigVolumeGrowth;

    // +++++++++++++++++++++++++++++++ BuyFastGrowth strategy

    // @Scheduled(fixedDelayString = "${strategy.buyFastGrowth.collectPairsToBuy.fixedDelay}", initialDelayString = "${strategy.buyFastGrowth.collectPairsToBuy.initialDelay}")
    // private void buyFastGrowth_collectPairsToBuy() {
    //     System.out.println("Collecting pairs runs");
    //     log.info("Test logging info");
    //     log.info("Found next pairs to buy:");
    //     buyFastGrowth.addPairsToBuy("USDT").stream().forEach(element -> {
    //         log.info(element.getSymbol() + " changed to " + element.getPriceChangePercent() + "%");
    //     });
    // }
    
    // @Scheduled(fixedDelayString = "${strategy.buyFastGrowth.buyCollectedPairs.fixedDelay}", initialDelayString = "${strategy.buyFastGrowth.buyCollectedPairs.initialDelay}")
    // private void buyFastGrowth_buyCollectedPairs() {
    //     log.info("Buying collected pairs runs");
    //     buyFastGrowth.makeOrdersForSelectedPairsToBuy();
    // }

    // @Scheduled(fixedDelayString = "${strategy.buyFastGrowth.closeOpenedPositions.fixedDelay}", initialDelayString = "${strategy.buyFastGrowth.closeOpenedPositions.initialDelay}")
    // private void buyFastGrowth_closeOpenedPositions() {
    //     log.info("Closing opened positions runs");
    //     buyFastGrowth.closeOpenedPositions();
    // }

    // ------------------------------- BuyFastGrowth strategy

    // +++++++++++++++++++++++++++++++ BuyBigVolumeGrowth strategy

    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.fillCheapPairs.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.fillCheapPairs.initialDelay}")
    private void buyBigVolumeGrowth_fillCheapPairs() {
        log.info("Fill cheap pairs from strategy runner.");
        buyBigVolumeGrowth.fillCheapPairs();
    }

    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.updateMonitoredCandleSticks.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.updateMonitoredCandleSticks.initialDelay}")
    private void buyBigVolumeGrowth_updateMonitoredCandleSticks() {
        log.info("Find big volume growth from strategy runner.");
        Closeable candlestickEventStream = buyBigVolumeGrowth.updateMonitoredCandleSticks();
        try {
            Thread.sleep(25_000);
            candlestickEventStream.close();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
        
        buyBigVolumeGrowth_buyGrownAssets();
    }

    // @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.buyGrownAssets.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.buyGrownAssets.initialDelay}")
    private void buyBigVolumeGrowth_buyGrownAssets() {
        log.info("Buy grown assets from strategy runner.");
        buyBigVolumeGrowth.buyGrownAssets();
    }

    // ------------------------------- BuyBigVolumeGrowth strategy
}
