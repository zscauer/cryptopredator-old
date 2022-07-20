package ru.tyumentsev.binancetestbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.market.CandlestickInterval;

import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.strategy.BuyBigVolumeGrowth;
import ru.tyumentsev.binancetestbot.strategy.BuyFastGrowth;

@Service
@Log4j2
public class StrategyRunner {

    @Autowired
    BuyFastGrowth buyFastGrowth;
    @Autowired
    BuyBigVolumeGrowth buyBigVolumeGrowth;
    @Autowired
    MarketData marketData;

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
        log.info("Fill cheap pairs from strategy runner.");
        buyBigVolumeGrowth.fillCheapPairs("USDT");
    }

    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.updateMonitoredCandleSticks.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.updateMonitoredCandleSticks.initialDelay}")
    private void buyBigVolumeGrowth_updateMonitoredCandles() {
        // log.info("Find big volume growth from strategy runner.");
        buyBigVolumeGrowth.updateMonitoredCandles("USDT", CandlestickInterval.HOURLY, 2);

        log.info("Monitored candles size is: " + marketData.getCachedCandles().size());
        // System.out.println(marketData.getMonitoredCandles());
        buyBigVolumeGrowth_CompareCandlesVolumes();
        buyBigVolumeGrowth_buyGrownAssets();
    }

    private void buyBigVolumeGrowth_CompareCandlesVolumes() {
        log.info("Compare candles volumes from strategy runner.");
        buyBigVolumeGrowth.compareCandlesVolumes();
    }

    // @Scheduled(fixedDelayString =
    // "${strategy.buyBigVolumeGrowth.buyGrownAssets.fixedDelay}",
    // initialDelayString =
    // "${strategy.buyBigVolumeGrowth.buyGrownAssets.initialDelay}")
    private void buyBigVolumeGrowth_buyGrownAssets() {
        log.info("Buy grown assets from strategy runner.");
        buyBigVolumeGrowth.buyGrownAssets();
    }

    @Scheduled(fixedDelayString = "${strategy.buyBigVolumeGrowth.checkOpenedPositions.fixedDelay}", initialDelayString = "${strategy.buyBigVolumeGrowth.checkOpenedPositions.initialDelay}")
    private void buyBigVolumeGrowth_checkOpenedPositions() {
        log.info("Check opened positions from strategy runner.");
        buyBigVolumeGrowth.checkMarketPositions();
    }

    // ------------------------------- BuyBigVolumeGrowth strategy
}
