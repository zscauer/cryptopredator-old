package ru.tyumentsev.cryptopredator.commons.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.AssetBalance;
import org.springframework.beans.factory.annotation.Value;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class SpotTrading implements TradingService {

    final AccountManager accountManager;
    final BinanceApiAsyncRestClient asyncRestClient;
    final MarketInfo marketInfo;

    @Value("${strategy.global.minimalAssetBalance}")
    int minimalAssetBalance;
    @Value("${strategy.global.baseOrderVolume}")
    int baseOrderVolume;

    public List<AssetBalance> getAccountBalances() {
        return accountManager.getAccountBalances();
    }

    public void placeBuyOrderFast(final String symbol, final String strategyName, final int strategyId, final float price, String quoteAsset) {
        if (Thread.holdsLock(this)) {
            log.warn("placeBuyOrderFast({}) object monitor already locked by the current thread {} ({}).", symbol, Thread.currentThread().getName(), Thread.currentThread().getId());
            return;
        }
        marketInfo.pairOrderPlaced(symbol, strategyName, baseOrderVolume / price, OrderSide.BUY);
        synchronized (this) {
            int availableOrdersCount = accountManager.getFreeAssetBalance(quoteAsset).intValue() / minimalAssetBalance;
            if (availableOrdersCount > 1) {
//            marketInfo.pairOrderPlaced(symbol);
//            placeLimitBuyOrderAtLastMarketPrice(symbol, baseOrderVolume / price);
                placeMarketBuyOrder(symbol, baseOrderVolume / price, strategyId);
            } else {
                marketInfo.pairOrderFilled(symbol, strategyName);
                log.debug("NOT enough balance to buy {}.", symbol);
            }
        }
    }

    public void placeSellOrderFast(final String symbol, final String strategyName, final int strategyId, final float qty) {
        if (Thread.holdsLock(this)) {
            log.info("placeSellOrderFast({}) object monitor already locked by the current thread {} ({}).", symbol, Thread.currentThread().getName(), Thread.currentThread().getId());
            return;
        }
        marketInfo.pairOrderPlaced(symbol, strategyName, qty, OrderSide.SELL);
        synchronized (this) {
            placeMarketSellOrder(symbol, qty, strategyId);
        }
    }

    public void placeLimitBuyOrderAtLastMarketPrice(String symbol, String strategyName, int strategyId, float quantity) {
//        log.debug("Try to place LIMIT BUY order: {} for {}.", symbol, quantity);
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            placeLimitBuyOrder(symbol, strategyName, strategyId, (float)Math.ceil(quantity),
                    orderBookResponse.getBids().get(0).getPrice());
        });
    }

    public void placeLimitSellOrderAtLastMarketPrice(String symbol, String strategyName, int strategyId, float quantity) {
//        log.debug("Try to place LIMIT SELL order: {} for {}.", symbol, quantity);
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            placeLimitSellOrder(symbol, strategyName, strategyId, quantity,
                    orderBookResponse.getAsks().get(0).getPrice());
        });
    }

    public void placeLimitBuyOrder(String symbol, String strategyName, int strategyId, float quantity, String price) {
        log.debug("Sending async request to place new limit order to buy {} {} at {}.", quantity, symbol, price);
        asyncRestClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC, Float.toString(quantity), price, strategyId), limitBuyResponse -> {
            marketInfo.pairOrderPlaced(symbol, strategyName, quantity, OrderSide.BUY);
            log.debug("Async LIMIT BUY order placed: {}", limitBuyResponse);
        });
    }

    public void placeLimitSellOrder(String symbol, String strategyName, int strategyId, float quantity, String price) {
        asyncRestClient.newOrder(NewOrder.limitSell(symbol, TimeInForce.GTC, Float.toString(quantity), price, strategyId), limitSellResponse -> {
            marketInfo.pairOrderPlaced(symbol, strategyName, quantity, OrderSide.SELL);
            log.debug("Async LIMIT SELL order placed: {}", limitSellResponse);
        });
    }

    public void placeMarketBuyOrder(String symbol, float quantity, int strategyId) {
        asyncRestClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity)), strategyId),
                marketBuyOrderCallback -> {
                    log.debug("Async MARKET BUY order placed: {}", marketBuyOrderCallback);
                });
    }

    public void placeMarketSellOrder(String symbol, float quantity, int strategyId) {
        asyncRestClient.newOrder(NewOrder.marketSell(symbol, String.valueOf(quantity), strategyId),
                marketSellOrderCallback -> {
                    log.debug("Async MARKET SELL order placed: {}", marketSellOrderCallback);
                });
    }

    public List<AssetBalance> recieveOpenedLongPositionsFromMarket() {
        return accountManager.refreshAccountBalances()
                .getAccountBalances().stream()
                .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB")))
                .collect(Collectors.toList());
    }

    public void closePostitions(Map<String, Float> positionsToClose) {
        log.debug("Start to go out at last price from {} positions to close:\n{}", positionsToClose.size(), positionsToClose);
        positionsToClose.forEach((key, value) -> placeMarketSellOrder(key, value, 0));
    }
}

