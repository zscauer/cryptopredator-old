package ru.tyumentsev.binancespotbot.service;

import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import io.micrometer.core.annotation.Timed;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.binancespotbot.strategy.TradingStrategy;

import java.io.Closeable;
import java.io.IOException;
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

    final Map<String, TradingStrategy> tradingStrategies;

    @Getter
    Closeable userDataUpdateEventsListener;

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
    @Value("${strategy.monitoring.averagingEnabled}")
    boolean averagingEnabled;
    @Value("${strategy.monitoring.averagingTriggerFactor}")
    double averagingTriggerFactor;

    private static Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    @Scheduled(fixedDelayString = "${strategy.global.initializeUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.initializeUserDataUpdateStream.initialDelay}")
    public void generalMonitoring_initializeAliveUserDataUpdateStream() {
        if (!testLaunch) {
            // User data stream are closing by binance after 24 hours of opening.
            accountManager.initializeUserDataUpdateStream();

            if (userDataUpdateEventsListener != null) {
                try {
                    userDataUpdateEventsListener.close();
                } catch (IOException e) {
                    log.error("Error while trying to close user data update events listener:\n{}.", e.getMessage());
                    e.printStackTrace();
                }
            }
            monitorUserDataUpdateEvents();
        }
    }

    @Scheduled(fixedDelayString = "${strategy.global.keepAliveUserDataUpdateStream.fixedDelay}", initialDelayString = "${strategy.global.keepAliveUserDataUpdateStream.initialDelay}")
    public void generalMonitoring_keepAliveUserDataUpdateStream() {
        if (!testLaunch) {
            accountManager.keepAliveUserDataUpdateStream();
        }
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

    /**
     * Opens web socket stream of user data update events and monitors trade events.
     * If it was "buy" event, then add pair from this event to monitoring,
     * if it was "sell" event - removes from monitoring.
     */
    public void monitorUserDataUpdateEvents() {
        userDataUpdateEventsListener = accountManager.listenUserDataUpdateEvents(callback -> {
            if (callback.getEventType() == UserDataUpdateEvent.UserDataUpdateEventType.ORDER_TRADE_UPDATE
                    && callback.getOrderTradeUpdateEvent().getExecutionType() == ExecutionType.TRADE) {
                OrderTradeUpdateEvent event = callback.getOrderTradeUpdateEvent();
                // if price == 0 most likely it was market order, use last market price.
                Double dealPrice = parsedDouble(event.getPrice()) == 0
                        ? parsedDouble(marketInfo.getLastTickerPrice(event.getSymbol()).getPrice())
                        : parsedDouble(event.getPrice());

                switch (event.getSide()) {
                    case BUY -> {
                        log.debug("Buy order trade updated, put result in opened positions cache: buy {} {} at {}.",
                                event.getOriginalQuantity(), event.getSymbol(), dealPrice);
                        marketData.putLongPositionToPriceMonitoring(event.getSymbol(), dealPrice, parsedDouble(event.getOriginalQuantity()));
                        tradingStrategies.values().forEach(strategy -> strategy.handleBuying(event));
                    }
                    case SELL -> {
                        log.debug("Sell order trade updated, remove result from opened positions cache: sell {} {} at {}.",
                                event.getOriginalQuantity(), event.getSymbol(), dealPrice);
                        marketData.removeLongPositionFromPriceMonitoring(event.getSymbol());
                        tradingStrategies.values().forEach(strategy -> strategy.handleSelling(event));
                    }
                }
            }
        });
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

            if (averagingEnabled && assetPrice > openedPosition.avgPrice() * averagingTriggerFactor) {
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
