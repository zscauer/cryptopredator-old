package ru.tyumentsev.binancespotbot.strategy;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;

import io.micrometer.core.annotation.Timed;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.service.AccountManager;
import ru.tyumentsev.binancespotbot.service.MarketInfo;
import ru.tyumentsev.binancespotbot.service.SpotTrading;

/**
 * This strategy will get two last candlesticks for each quote USDT pair
 * and buy this asset if volume has grown more then priceGrowthFactor against previous candle.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Log4j2
public class BuyBigVolumeGrowth {

    @Getter
    final MarketInfo marketInfo;
    final MarketData marketData;
    final SpotTrading spotTrading;
    @Getter
    final AccountManager accountManager;
    @Getter
    Closeable userDataUpdateEventsListener;

    @Value("${strategy.buyBigVolumeGrowth.matchTrend}")
    boolean matchTrend;
    @Value("${strategy.buyBigVolumeGrowth.maximalPairPrice}")
    int maximalPairPrice;
    @Value("${strategy.buyBigVolumeGrowth.minimalAssetBalance}")
    int minimalAssetBalance;
    @Value("${strategy.buyBigVolumeGrowth.baseOrderVolume}")
    int baseOrderVolume;
    @Value("${strategy.buyBigVolumeGrowth.volumeGrowthFactor}")
    int volumeGrowthFactor;
    @Value("${strategy.buyBigVolumeGrowth.priceGrowthFactor}")
    double priceGrowthFactor;
    @Value("${strategy.buyBigVolumeGrowth.priceDecreaseFactor}")
    double priceDecreaseFactor;
    @Value("${strategy.global.rocketFactor}")
    double rocketFactor;

    private static Double parsedPrice(String priceToParse) {
        return Double.parseDouble(priceToParse);
    }

    public void fillCheapPairs(String asset) {
        // get all pairs, that trades against USDT.
        List<String> availablePairs = marketData.getAvailablePairs(asset);
        // filter available pairs to get cheaper then maximalPairPrice.
        List<String> filteredPairs = marketInfo
                .getLastTickersPrices(
                        marketData.getAvailablePairsSymbolsFormatted(availablePairs, 0, availablePairs.size() - 1))
                .stream().filter(tickerPrice -> parsedPrice(tickerPrice.getPrice()) < maximalPairPrice)
                .map(TickerPrice::getSymbol).collect(Collectors.toCollection(ArrayList::new));
        log.info("Filtered {} cheap tickers.", filteredPairs.size());

        // place filtered pairs to cache.
        marketData.putCheapPairs(asset, filteredPairs);
    }

    /**
     * Add to cache information about last candles of all filtered pairs (exclude
     * opened positions).
     * 
     * @param asset
     * @param interval
     * @param limit
     */
    @Timed("updateMonitoredCandles")
    public void updateMonitoredCandles(String asset, CandlestickInterval interval, Integer limit) {
        marketData.clearCandleSticksCache();
        marketData.getCheapPairsExcludeOpenedPositions(asset).stream()
                .forEach(ticker -> {
                    marketData.addCandlesticksToCache(ticker,
                            marketInfo.getCandleSticks(ticker, interval, limit));
                });
        log.debug("Cache of candle sticks updated and now contains {} pairs.", marketData.getCachedCandles().size());
    }

    // compare volumes in current and previous candles to find big volume growth.
    @Timed("findGrownAssets")
    public void findGrownAssets() {
        Map<String, List<Candlestick>> cachedCandlesticks = marketData.getCachedCandles();

        try {
            cachedCandlesticks.entrySet().stream() // current volume & current price bigger then previous:
                    .filter(entrySet -> parsedPrice(entrySet.getValue().get(1)
                            .getVolume()) > parsedPrice(entrySet.getValue().get(0).getVolume()) * volumeGrowthFactor
                            && parsedPrice(entrySet.getValue().get(1).getClose()) > parsedPrice(
                                    entrySet.getValue().get(0).getClose()) * priceGrowthFactor)
                    .forEach(entrySet -> addPairToBuy(entrySet.getKey(),
                            parsedPrice(entrySet.getValue().get(1).getClose())));
        } catch (Exception e) {
            log.error("Error while trying to find grown assets:\n{}.", e.getMessage());
            e.printStackTrace();
        }
    }

    public void addPairToBuy(String symbol, Double price) {
        if (matchTrend) {
            List<Candlestick> candleSticks = marketInfo.getCandleSticks(symbol, CandlestickInterval.DAILY, 2);
            if (pairHadTradesInThePast(candleSticks, 2)
                    && price > parsedPrice(candleSticks.get(0).getHigh())) {
                marketData.putPairToBuy(symbol, price);
            }
        } else {
            marketData.putPairToBuy(symbol, price);
        }
    }

    @Timed("buyGrownAssets")
    public void buyGrownAssets(String asset) {
        Map<String, Double> pairsToBuy = marketData.getPairsToBuy();
        log.debug("There is {} pairs to buy: {}.", pairsToBuy.size(), pairsToBuy);

        if (pairsToBuy.size() > 0) {
            int availableOrdersCount = accountManager.getFreeAssetBalance(asset).intValue() / minimalAssetBalance;
            Map<String, Double> remainsPairs = new HashMap<>(); // collect pairs which can't be bought.

            for (Entry<String, Double> pairToBuy : pairsToBuy.entrySet()) {
                if (availableOrdersCount > 0
                        && pairHadTradesInThePast(pairToBuy.getKey(), CandlestickInterval.DAILY, 3)) {
                    spotTrading.placeLimitBuyOrderAtLastMarketPrice(pairToBuy.getKey(),
                            baseOrderVolume / pairToBuy.getValue());
                    availableOrdersCount--;
                } else {
                    remainsPairs.put(pairToBuy.getKey(), pairToBuy.getValue());
                }
            }

            if (!remainsPairs.isEmpty()) {
                log.info("NOT ENOUGH FREE BALANCE to buy next pairs: {}.", remainsPairs);
                remainsPairs.clear();
            }

            pairsToBuy.clear();
        }
    }

    public boolean pairHadTradesInThePast(String ticker, CandlestickInterval interval, Integer qtyBarsToAnalize) {
        // pair should have history of trade for some days before.
        return marketInfo.getCandleSticks(ticker, interval, qtyBarsToAnalize).size() == qtyBarsToAnalize;
    }

    public boolean pairHadTradesInThePast(List<Candlestick> candleSticks, int qtyBarsToAnalize) {
        // pair should have history of trade for some days before.
        return candleSticks.size() == qtyBarsToAnalize;
    }

    @Timed("checkMarketPositions")
    public void checkMarketPositions(String quoteAsset) {
        Map<String, Double> openedPositionsLastPrices = marketData.getOpenedPositionsLastPrices();
        log.debug("There is {} prices cached in openedPositionsLastPrices.", openedPositionsLastPrices.size());

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
        List<TickerPrice> currentPrices = marketInfo
                .getLastTickersPrices(
                        marketData.getAvailablePairsSymbolsFormatted(pairsQuoteAssetOnBalance, 0,
                                pairsQuoteAssetOnBalance.size()));

        currentPrices.stream().forEach(tickerPrice -> {
            Double currentPrice = parsedPrice(tickerPrice.getPrice());
            String tickerSymbol = tickerPrice.getSymbol();

            if (openedPositionsLastPrices.get(tickerSymbol) == null) {
                log.info("'{}' not found in opened positions last prices, last prices contains:\n{}", tickerSymbol,
                        openedPositionsLastPrices);
            } else if (currentPrice > openedPositionsLastPrices.get(tickerSymbol)) {
                // update current price if it growth.
                log.debug("PRICE of {} GROWTH and now equals {}.", tickerSymbol, currentPrice);
                marketData.putOpenedPositionToPriceMonitoring(tickerSymbol, currentPrice);
            } else if (currentPrice < openedPositionsLastPrices.get(tickerSymbol) * priceDecreaseFactor) {
                // close position if price decreased.
                log.debug("PRICE of {} DECREASED and now equals {}.", tickerSymbol, currentPrice);
                addPairToSell(tickerSymbol, quoteAsset, positionsToClose);
            }
        });
        spotTrading.closeAllPostitions(positionsToClose);
    }

    private void addPairToSell(String tickerSymbol, String quoteAsset, Map<String, Double> positionsToClose) {
        if (matchTrend) {
            List<Candlestick> candleSticks = marketInfo.getCandleSticks(tickerSymbol, CandlestickInterval.DAILY, 2);
            if (pairHadTradesInThePast(candleSticks, 2)) {
                // current price higher then close price of previous day more then rocketFactor
                // - there is rocket.
                if (parsedPrice(candleSticks.get(1).getClose()) > parsedPrice(candleSticks.get(0).getClose())
                        * rocketFactor) {
                    positionsToClose.put(tickerSymbol,
                            accountManager.getFreeAssetBalance(tickerSymbol.replace(quoteAsset, "")));
                } else if (parsedPrice(candleSticks.get(0).getClose()) > parsedPrice(candleSticks.get(1).getClose())
                        * priceGrowthFactor) { // close price of previous day is higher then current more then growth
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

    /**
     * Opens web socket stream of user data update events and monitors trade events.
     * If it was "buy" event, than add pair from this event to monitoring,
     * if it was "sell" event - removes from monitoring.
     */
    public void monitorUserDataUpdateEvents() {
        userDataUpdateEventsListener = accountManager.listenUserDataUpdateEvents(callback -> {
            if (callback.getEventType() == UserDataUpdateEventType.ORDER_TRADE_UPDATE
                    && callback.getOrderTradeUpdateEvent().getExecutionType() == ExecutionType.TRADE) {
                OrderTradeUpdateEvent event = callback.getOrderTradeUpdateEvent();
                // if price == 0 most likely it was market order, use last market price.
                Double dealPrice = parsedPrice(event.getPrice()) == 0
                        ? parsedPrice(marketInfo.getLastTickerPrice(event.getSymbol()).getPrice())
                        : parsedPrice(event.getPrice());

                if (event.getSide() == OrderSide.BUY) {
                    log.debug("Buy order trade updated, put result in opened positions cache: {} at {}.",
                            event.getSymbol(), dealPrice);
                    marketData.putOpenedPositionToPriceMonitoring(event.getSymbol(), dealPrice);
                } else {
                    log.debug("Sell order trade updated, remove result from opened positions cache: {} at {}.",
                            event.getSymbol(), dealPrice);
                    marketData.removeClosedPositionFromPriceMonitoring(event.getSymbol());
                }
            }
        });
    }
}
