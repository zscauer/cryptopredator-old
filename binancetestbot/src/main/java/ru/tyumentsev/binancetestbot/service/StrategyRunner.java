package ru.tyumentsev.binancetestbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancetestbot.strategy.BuyFastGrowth;

@Service
@Log4j2
public class StrategyRunner {

    @Autowired
    BuyFastGrowth buyFastGrowth;

    @Scheduled(fixedDelayString = "${strategy.buyFastGrowth.collectPairsToBuy.fixedDelay}", initialDelayString = "${strategy.buyFastGrowth.collectPairsToBuy.initialDelay}")
    private void buyFastGrowth_collectPairsToBuy() {
        System.out.println("Collecting pairs runs");
        log.info("Test logging info");
        log.info("Found next pairs to buy:");
        buyFastGrowth.addPairsToBuy("USDT").stream().forEach(element -> {
            log.info(element.getSymbol() + " changed to " + element.getPriceChangePercent() + "%");
        });
    }
    
    @Scheduled(fixedDelayString = "${strategy.buyFastGrowth.buyCollectedPairs.fixedDelay}", initialDelayString = "${strategy.buyFastGrowth.buyCollectedPairs.initialDelay}")
    private void buyFastGrowth_buyCollectedPairs() {
        log.info("Buying collected pairs runs");
        buyFastGrowth.makeOrdersForSelectedPairsToBuy();
    }

    @Scheduled(fixedDelayString = "${strategy.buyFastGrowth.closeOpenedPositions.fixedDelay}", initialDelayString = "${strategy.buyFastGrowth.closeOpenedPositions.initialDelay}")
    private void buyFastGrowth_closeOpenedPositions() {
        log.info("Closing opened positions runs");
        buyFastGrowth.closeOpenedPositions();
    }
}
