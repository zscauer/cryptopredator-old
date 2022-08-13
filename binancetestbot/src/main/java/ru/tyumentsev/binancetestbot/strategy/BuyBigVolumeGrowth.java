package ru.tyumentsev.binancetestbot.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.service.AccountManager;
import ru.tyumentsev.binancetestbot.service.MarketInfo;
import ru.tyumentsev.binancetestbot.service.SpotTrading;

@Service
@RequiredArgsConstructor
@Log4j2
public class BuyBigVolumeGrowth {

    /*
     * this strategy will get two last candlesticks for each quote USDT pair
     * and buy this coin if volume has grown more then 3x against last candle.
     */

    @Autowired
    @Getter
    MarketInfo marketInfo;
    @Autowired
    MarketData marketData;
    @Autowired
    SpotTrading spotTrading;
    @Autowired
    @Getter
    AccountManager accountManager;
    @Autowired
    BinanceApiWebSocketClient binanceApiWebSocketClient;

    public void fillCheapPairs(String asset) {
        // get all pairs, that trades against USDT.
        List<String> availablePairs = marketData.getAvailablePairs(asset);
        // filter available pairs to get cheaper then 1 USDT.
        List<String> filteredPairs = marketInfo
                .getLastTickersPrices(
                        marketData.getAvailablePairsSymbolsFormatted(availablePairs, 0, availablePairs.size() - 1))
                .stream().filter(tickerPrice -> Double.parseDouble(tickerPrice.getPrice()) < 1)
                .map(TickerPrice::getSymbol).collect(Collectors.toCollection(ArrayList::new));
        log.info("Filtered " + filteredPairs.size() + " cheap tickers.");

        // place filtered pairs to cache.
        marketData.putCheapPairs(asset, filteredPairs);
    }

    /**
     * Add to cache information about last candles of all filtered pairs(exclude
     * opened positions).
     * 
     * @param asset
     * @param interval
     * @param limit
     */
    public void updateMonitoredCandles(String asset, CandlestickInterval interval, Integer limit) {
        marketData.clearCandleSticksCache();
        marketData.getCheapPairsWithoutOpenedPositions(asset).stream().forEach(ticker -> {
            marketData.addCandlesticksToCache(ticker,
                    marketInfo.getCandleSticks(ticker, interval, limit));
        });
    }

    // compare volumes in current and previous candles to find big volume growth.
    public void compareCandlesVolumes() {
        Map<String, List<Candlestick>> cachedCandlesticks = marketData.getCachedCandles();

        cachedCandlesticks.entrySet().stream()
                .filter(entrySet -> Double.parseDouble(entrySet.getValue().get(1).getVolume()) > Double
                        .parseDouble(entrySet.getValue().get(0).getVolume()) * 2.5 // current volume bigger then
                                                                                   // previous.
                        && Double.parseDouble(entrySet.getValue().get(1).getClose()) > Double
                                .parseDouble(entrySet.getValue().get(0).getClose()) * 1.03) // current price bigger then
                                                                                            // previous.
                .forEach(entrySet -> marketData.addPairToTestBuy(entrySet.getKey(),
                        Double.parseDouble(entrySet.getValue().get(1).getClose()))); // add this pairs to buy.
    }

    public void buyGrownAssets() {
        Map<String, Double> pairsToBuy = marketData.getTestMapToBuy();

        if (pairsToBuy.size() > 0) {
            log.info("There is " + pairsToBuy.size() + " elements in test map to buy: " + pairsToBuy);
            if (accountManager.getFreeAssetBalance("USDT") > 13 * pairsToBuy.size()) {
                for (Map.Entry<String, Double> entrySet : pairsToBuy.entrySet()) {
                    spotTrading.placeLimitBuyOrderAtLastMarketPrice(entrySet.getKey(), 11 / entrySet.getValue());
                }
            }
            pairsToBuy.clear();
        } else {
            // log.info("Nothing to buy");
        }
    }

    public void checkMarketPositions(String quoteAsset) { // real deals
        Map<String, Double> openedPositionsLastPrices = marketData.getOpenedPositionsLastPrices();
        List<AssetBalance> currentBalances = accountManager.getAccountBalances().stream()
                .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();

        if (currentBalances.isEmpty()) {
            return;
        }

        Map<String, Double> positionsToClose = new HashMap<>();

        List<String> pairsQuoteAssetsOnBalance = currentBalances.stream()
                .map(balance -> balance.getAsset() + quoteAsset)
                .toList();
        List<TickerPrice> currentPrices = marketInfo
                .getLastTickersPrices(
                        marketData.getAvailablePairsSymbolsFormatted(pairsQuoteAssetsOnBalance, 0,
                                pairsQuoteAssetsOnBalance.size()));

        currentPrices.stream().forEach(tickerPrice -> {
            Double currentPrice = Double.parseDouble(tickerPrice.getPrice());
            if (currentPrice > openedPositionsLastPrices.get(tickerPrice.getSymbol())) {
                // update current price if it growth
                log.info("Price of " + tickerPrice.getSymbol() + " growth and now equals " + currentPrice);
                marketData.putOpenedPositionToPriceMonitoring(tickerPrice.getSymbol(), currentPrice);
            } else if (currentPrice < openedPositionsLastPrices.get(tickerPrice.getSymbol()) * 0.93) {
                // close position if price decreased
                positionsToClose.put(tickerPrice.getSymbol(),
                        accountManager.getFreeAssetBalance(tickerPrice.getSymbol().replace(quoteAsset, "")));
            }
        });

        log.info("!!! Prices of " + positionsToClose.size() + " pairs decreased, selling: " + positionsToClose);
        spotTrading.closeAllPostitions(positionsToClose);

        openedPositionsLastPrices.keySet().removeAll(positionsToClose.keySet());
    }

    public void monitoringUserDataUpdateEvents() {
        accountManager.listenUserDataUpdateEvents(callback -> {
            if (callback.getEventType() == UserDataUpdateEventType.ORDER_TRADE_UPDATE
                    && callback.getOrderTradeUpdateEvent().getExecutionType() == ExecutionType.TRADE) {
                OrderTradeUpdateEvent event = callback.getOrderTradeUpdateEvent();
                if (event.getSide() == OrderSide.BUY) {
                    log.info("Order trade updated, put result in opened positions cache: " + event.getSymbol() + " "
                            + event.getPrice());
                    marketData.putOpenedPositionToPriceMonitoring(event.getSymbol(),
                            Double.parseDouble(event.getPrice()));
                } else {
                    log.info("Order trade updated, remove result from opened positions cache: "
                            + event.getSymbol() + " " + event.getPrice());
                    marketData.removeClosedPositoinFromPriceMonitoring(event.getSymbol());
                }
            }
        });
    }

    @Deprecated
    public void checkOpenedPositions() { // test method
        Map<String, Double> openedPositions = marketData.getOpenedPositionsLastPrices();

        if (openedPositions.isEmpty()) {
            return;
        }

        Map<String, Double> positionsToClose = new HashMap<>();

        List<TickerPrice> currentPrices = marketInfo.getLastTickersPrices(marketData.getAvailablePairsSymbolsFormatted(
                new ArrayList<>(openedPositions.keySet()), 0, openedPositions.size()));

        currentPrices.stream().forEach(tickerPrice -> {
            Double currentPrice = Double.parseDouble(tickerPrice.getPrice());
            if (currentPrice > openedPositions.get(tickerPrice.getSymbol())) { // update current price if it growth
                log.info("Price of " + tickerPrice.getSymbol() + " growth and now equals " + currentPrice);
                marketData.putOpenedPositionToPriceMonitoring(tickerPrice.getSymbol(), currentPrice);
            } else if (currentPrice < openedPositions.get(tickerPrice.getSymbol()) * 0.93) {
                // close position if price decreased
                positionsToClose.put(tickerPrice.getSymbol(), currentPrice);
            }
        });

        log.info("!!! Prices of " + positionsToClose.size() + " pairs decreased, selling: "
                + positionsToClose.toString());
        marketData.representClosingPositions(positionsToClose, "USDT");
    }

}
