package ru.tyumentsev.binancespotbot.strategy;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.service.AccountManager;
import ru.tyumentsev.binancespotbot.service.MarketInfo;
import ru.tyumentsev.binancespotbot.service.SpotTrading;

/**
 * This strategy will get two last candlesticks for each quote USDT pair
 * and buy this coin if volume has grown more then 3x against last candle.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class BuyBigVolumeGrowth {

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
    @Getter
    Closeable userDataUpdateEventsListener;

    @Value("${strategy.buyBigVolumeGrowth.maximalPairPrice}")
    int maximalPairPrice;
    @Value("${strategy.buyBigVolumeGrowth.minimalAssetBalance}")
    int minimalAssetBalance;
    @Value("${strategy.buyBigVolumeGrowth.baseOrderVolume}")
    int baseOrderVolume;

    public void fillCheapPairs(String asset) {
        // get all pairs, that trades against USDT.
        List<String> availablePairs = marketData.getAvailablePairs(asset);
        // filter available pairs to get cheaper then maximalPairPrice.
        List<String> filteredPairs = marketInfo
                .getLastTickersPrices(
                        marketData.getAvailablePairsSymbolsFormatted(availablePairs, 0, availablePairs.size() - 1))
                .stream().filter(tickerPrice -> Double.parseDouble(tickerPrice.getPrice()) < maximalPairPrice)
                .map(TickerPrice::getSymbol).collect(Collectors.toCollection(ArrayList::new));
        log.info("Filtered {} cheap tickers.", filteredPairs.size());

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
                .forEach(entrySet -> marketData.addPairToBuy(entrySet.getKey(),
                        Double.parseDouble(entrySet.getValue().get(1).getClose()))); // add this pairs to buy.
    }

    public void buyGrownAssets(String asset) {
        Map<String, Double> pairsToBuy = marketData.getPairsToBuy();

        if (pairsToBuy.size() > 0) {
            // log.info("There is {} elements in test map to buy: {}", pairsToBuy.size(),
            // pairsToBuy);
            if (accountManager.getFreeAssetBalance(asset) > minimalAssetBalance * pairsToBuy.size()) {
                for (Map.Entry<String, Double> entrySet : pairsToBuy.entrySet()) {
                    spotTrading.placeLimitBuyOrderAtLastMarketPrice(entrySet.getKey(),
                            baseOrderVolume / entrySet.getValue());
                }
            }
            pairsToBuy.clear();
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
            String tickerSymbol = tickerPrice.getSymbol();
            if (openedPositionsLastPrices.get(tickerSymbol) == null) {
                log.info("{} not found in opened positions last prices\n last prices contains: {}", tickerSymbol,
                        openedPositionsLastPrices);
            } else if (currentPrice > openedPositionsLastPrices.get(tickerSymbol)) {
                // update current price if it growth
                // log.info("Price of {} growth and now equals {}", tickerSymbol, currentPrice);
                marketData.putOpenedPositionToPriceMonitoring(tickerSymbol, currentPrice);
            } else if (currentPrice < openedPositionsLastPrices.get(tickerSymbol) * 0.93) {
                // close position if price decreased
                positionsToClose.put(tickerSymbol,
                        accountManager.getFreeAssetBalance(tickerSymbol.replace(quoteAsset, "")));
            }
        });
        spotTrading.closeAllPostitions(positionsToClose);
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
                Double dealPrice = Double.parseDouble(event.getPrice()) == 0
                        ? Double.parseDouble(marketInfo.getLastTickerPrice(event.getSymbol()).getPrice())
                        : Double.parseDouble(event.getPrice());

                if (event.getSide() == OrderSide.BUY) {
                    log.info("Buy order trade updated, put result in opened positions cache: {} at {}",
                            event.getSymbol(), dealPrice);
                    marketData.putOpenedPositionToPriceMonitoring(event.getSymbol(), dealPrice);
                } else {
                    log.info("Sell order trade updated, remove result from opened positions cache: {} at {}",
                            event.getSymbol(), dealPrice);
                    marketData.removeClosedPositionFromPriceMonitoring(event.getSymbol());
                }
            }
        });
    }
}