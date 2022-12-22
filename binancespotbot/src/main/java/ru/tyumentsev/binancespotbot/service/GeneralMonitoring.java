package ru.tyumentsev.binancespotbot.service;

import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.domain.OpenedPosition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class GeneralMonitoring {

    // TODO: move this functionality to separate application
    final AccountManager accountManager;
    final MarketData marketData;
    final MarketInfo marketInfo;
    final SpotTrading spotTrading;

    final String USDT = "USDT";

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.global.rocketFactor}")
    double rocketFactor;
    @Value("${strategy.monitoring.matchTrend}")
    boolean matchTrend;
    @Value("${strategy.monitoring.priceDecreaseFactor}")
    double priceDecreaseFactor;
    @Value("${strategy.monitoring.priceGrowthFactor}")
    double priceGrowthFactor;
    @Value("${strategy.monitoring.averagingTriggerFactor}")
    double averagingTriggerFactor;

    private static Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    /**
     * Search for not executed orders to cancel them.
     */
    @Scheduled(fixedDelayString = "${strategy.monitoring.cancelExpiredOrders.fixedDelay}", initialDelayString = "${strategy.monitoring.cancelExpiredOrders.initialDelay}")
    public void generalMonitoring_cancelExpiredOrders() {
        // TODO: find way to get info about active orders
    }

    @Timed("checkOpenedPositions")
    @Scheduled(fixedDelayString = "${strategy.monitoring.checkOpenedPositions.fixedDelay}", initialDelayString = "${strategy.monitoring.checkOpenedPositions.initialDelay}")
    public void generalMonitoring_checkOpenedPositions() {
        if (!testLaunch) {
            checkMarketPositions(USDT);
        }
    }

    @Timed("checkMarketPositions")
    public void checkMarketPositions(final String quoteAsset) {
        Map<String, OpenedPosition> openedPositions = marketData.getLongPositions();
        log.debug("There is {} prices cached in openedPositions.", openedPositions.size());

        List<AssetBalance> currentBalances = accountManager.getAccountBalances().stream()
                .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();

        if (currentBalances.isEmpty()) {
            log.debug("No available trading assets found on binance account.");
            return;
        }

        Map<String, Double> positionsToClose = new HashMap<>();

        List<String> pairsQuoteAssetOnBalance = currentBalances.stream()
                .map(balance -> balance.getAsset() + quoteAsset)
                .toList();
        // TODO: define max price not by current, but by max price of candle?
        List<TickerPrice> currentPrices = marketInfo
                .getLastTickersPrices(
                        marketData.combinePairsToRequestString(pairsQuoteAssetOnBalance));
        Map<String, Double> assetsToBuy = new HashMap<>();

        for (TickerPrice tickerPrice : currentPrices) {
            Double assetPrice = parsedDouble(tickerPrice.getPrice());
            String tickerSymbol = tickerPrice.getSymbol();
            OpenedPosition openedPosition = openedPositions.get(tickerSymbol);

            if (openedPosition == null) {
                log.info("'{}' not found in opened positions last prices, last prices contains:\n{}",
                        tickerSymbol, openedPositions);
                continue;
            }
            if (assetPrice > openedPosition.maxPrice()) {
                // update current price if it's growth.
                marketData.updateOpenedPositionMaxPrice(tickerSymbol, assetPrice, marketData.getLongPositions());
            }

            if (assetPrice > openedPosition.avgPrice() * averagingTriggerFactor) {
                log.debug("PRICE of {} GROWTH more than avg and now equals {}.", tickerSymbol, assetPrice);
                assetsToBuy.put(tickerSymbol, assetPrice);
            } else if (assetPrice < openedPosition.maxPrice() * priceDecreaseFactor) {
                // close position if price decreased.
                log.debug("PRICE of {} DECREASED and now equals {}.", tickerSymbol, assetPrice);
                addPairToSell(tickerSymbol, quoteAsset, positionsToClose);
            }
        }
        spotTrading.buyAssets(assetsToBuy, quoteAsset, accountManager);
        spotTrading.closePostitions(positionsToClose);
    }

    private void addPairToSell(String tickerSymbol, String quoteAsset, Map<String, Double> positionsToClose) {
        if (matchTrend) {
            List<Candlestick> candleSticks = marketInfo.getCandleSticks(tickerSymbol, CandlestickInterval.DAILY, 2);
            if (marketInfo.pairHadTradesInThePast(candleSticks, 2)) {
                // current price higher then close price of previous day more than rocketFactor
                // - there is rocket.
                if (parsedDouble(candleSticks.get(1).getClose()) > parsedDouble(candleSticks.get(0).getClose())
                        * rocketFactor) {
                    positionsToClose.put(tickerSymbol,
                            accountManager.getFreeAssetBalance(tickerSymbol.replace(quoteAsset, "")));
                } else if (parsedDouble(candleSticks.get(0).getClose()) > parsedDouble(candleSticks.get(1).getClose())
                        * priceGrowthFactor) { // close price of previous day is higher than current more than growth
                    // factor - there is downtrend.
                    positionsToClose.put(tickerSymbol,
                            accountManager.getFreeAssetBalance(tickerSymbol.replace(quoteAsset, "")));
                }
            }
        } else {
            positionsToClose.put(tickerSymbol,
                    accountManager.getFreeAssetBalance(tickerSymbol.replace(quoteAsset, "")));
        }
    }
}
