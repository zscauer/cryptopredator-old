package ru.tyumentsev.binancespotbot.strategy;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.OrderSide;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class BuyBigVolumeGrowth implements TradingStrategy {

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
    @Value("${strategy.buyBigVolumeGrowth.volumeGrowthFactor}")
    int volumeGrowthFactor;
    @Value("${strategy.buyBigVolumeGrowth.priceGrowthFactor}")
    double priceGrowthFactor;

    private static Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    // TODO: replace method to common class.
    public void fillCheapPairs(String asset) {
        // get all pairs, that trades against USDT.
        List<String> availablePairs = marketData.getAvailablePairs(asset);
        // filter available pairs to get cheaper than maximalPairPrice.
        List<String> filteredPairs = marketInfo
                .getLastTickersPrices(
                        marketData.combinePairsToRequestString(availablePairs))
                .stream().filter(tickerPrice -> parsedDouble(tickerPrice.getPrice()) < maximalPairPrice)
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
        marketData.getCheapPairsExcludeOpenedPositions(asset)
                .forEach(ticker -> marketData.addCandlesticksToCache(ticker,
                        marketInfo.getCandleSticks(ticker, interval, limit)));
        log.debug("Cache of candle sticks updated and now contains {} pairs.", marketData.getCachedCandles().size());
    }

    // compare volumes in current and previous candles to find big volume growth.
    @Timed("findGrownAssets")
    public void findGrownAssets() {
        Map<String, List<Candlestick>> cachedCandlesticks = marketData.getCachedCandles();

        try {
            cachedCandlesticks.entrySet().stream() // current volume & current price bigger than previous:
                    .filter(entrySet -> parsedDouble(entrySet.getValue().get(1)
                            .getVolume()) > parsedDouble(entrySet.getValue().get(0).getVolume()) * volumeGrowthFactor
                            && parsedDouble(entrySet.getValue().get(1).getClose()) > parsedDouble(
                            entrySet.getValue().get(0).getClose()) * priceGrowthFactor)
                    .forEach(entrySet -> addPairToBuy(entrySet.getKey(),
                            parsedDouble(entrySet.getValue().get(1).getClose())));
        } catch (Exception e) {
            log.error("Error while trying to find grown assets:\n{}.", e.getMessage());
            e.printStackTrace();
        }
    }

    public void addPairToBuy(String symbol, Double price) {
        if (matchTrend) {
            List<Candlestick> candleSticks = marketInfo.getCandleSticks(symbol, CandlestickInterval.DAILY, 2);
            if (marketInfo.pairHadTradesInThePast(candleSticks, 2)
                    && price > parsedDouble(candleSticks.get(0).getHigh())) {
                marketData.putPairToBuy(symbol, price);
            }
        } else {
            marketData.putPairToBuy(symbol, price);
        }
    }

    @Timed("buyGrownAssets")
    public void buyGrownAssets(String quoteAsset) {
        Map<String, Double> pairsToBuy = marketData.getPairsToBuy();
        log.debug("There is {} pairs to buy: {}.", pairsToBuy.size(), pairsToBuy);

        spotTrading.buyAssets(pairsToBuy, quoteAsset, accountManager);
    }

    /**
     * Opens web socket stream of user data update events and monitors trade events.
     * If it was "buy" event, then add pair from this event to monitoring,
     * if it was "sell" event - removes from monitoring.
     */
    public void monitorUserDataUpdateEvents() {
        userDataUpdateEventsListener = accountManager.listenUserDataUpdateEvents(callback -> {
            if (callback.getEventType() == UserDataUpdateEventType.ORDER_TRADE_UPDATE
                    && callback.getOrderTradeUpdateEvent().getExecutionType() == ExecutionType.TRADE) {
                OrderTradeUpdateEvent event = callback.getOrderTradeUpdateEvent();
                // if price == 0 most likely it was market order, use last market price.
                Double dealPrice = parsedDouble(event.getPrice()) == 0
                        ? parsedDouble(marketInfo.getLastTickerPrice(event.getSymbol()).getPrice())
                        : parsedDouble(event.getPrice());

                if (event.getSide() == OrderSide.BUY) {
                    log.debug("Buy order trade updated, put result in opened positions cache: buy {} {} at {}.",
                            event.getOriginalQuantity(), event.getSymbol(), dealPrice);
                    marketData.putLongPositionToPriceMonitoring(event.getSymbol(), dealPrice, parsedDouble(event.getOriginalQuantity()));
                } else {
                    log.debug("Sell order trade updated, remove result from opened positions cache: sell {} {} at {}.",
                            event.getOriginalQuantity(), event.getSymbol(), dealPrice);
                    marketData.removeLongPositionFromPriceMonitoring(event.getSymbol());
                }
            }
        });
    }
}
