package ru.tyumentsev.cryptopredator.commons.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.AssetBalance;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;

import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.exception.BinanceApiException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
@Slf4j
@SuppressWarnings("unused")
public class SpotTrading implements TradingService {

    final AccountInfo accountInfo;
    final BinanceApiAsyncRestClient asyncRestClient;
    final MarketInfo marketInfo;
    final BotStateService botStateService;

    final Map<Integer, Boolean> buyOrdersDelays = new HashMap<>();

    public List<AssetBalance> getAccountBalances() {
        return accountInfo.getAllAccountBalances();
    }

    public void placeBuyOrderFast(final String symbol, final int strategyId, final float price, String quoteAsset, final int minimalAssetBalance, final int baseOrderVolume) {
        if (Thread.holdsLock(this)) {
            log.warn("placeBuyOrderFast({}) object monitor already locked by the current thread {} ({}).", symbol, Thread.currentThread().getName(), Thread.currentThread().getId());
            return;
        }
        marketInfo.pairOrderPlaced(symbol, strategyId, baseOrderVolume / price, OrderSide.BUY);
        synchronized (this) {
            int availableOrdersCount = botStateService.getAvailableOrdersCount(strategyId); //accountInfo.getFreeAssetBalance(quoteAsset).intValue() / minimalAssetBalance;
            buyOrdersDelays.put(strategyId, availableOrdersCount < 4);
            if (availableOrdersCount > 0) {
                if (buyOrdersDelays.get(strategyId)) {
                    try {
                        Thread.sleep(300); // pause to not exceed strategy limit.
                    } catch (InterruptedException e) {
                        log.error("Thread error while trying to sleep before placing buy order of {}: {}.", symbol, e.getMessage());
                        marketInfo.pairOrderFilled(symbol, strategyId);
                    }
                }
                placeMarketBuyOrder(symbol, baseOrderVolume / price, strategyId);
            } else {
                marketInfo.pairOrderFilled(symbol, strategyId);
                log.debug("NOT enough balance to buy {}.", symbol);
            }
        }
    }

    public void placeSellOrderFast(final String symbol, final int strategyId, final float qty) {
        if (Thread.holdsLock(this)) {
            log.info("placeSellOrderFast({}) object monitor already locked by the current thread {} ({}).", symbol, Thread.currentThread().getName(), Thread.currentThread().getId());
            return;
        }
        marketInfo.pairOrderPlaced(symbol, strategyId, qty, OrderSide.SELL);
        synchronized (this) {
            placeMarketSellOrder(symbol, qty, strategyId);
        }
    }

    public void placeLimitBuyOrderAtLastMarketPrice(String symbol, int strategyId, float quantity) {
//        log.debug("Try to place LIMIT BUY order: {} for {}.", symbol, quantity);
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            placeLimitBuyOrder(symbol, strategyId, (float)Math.ceil(quantity),
                    orderBookResponse.getBids().get(0).getPrice());
        });
    }

    public void placeLimitSellOrderAtLastMarketPrice(String symbol, int strategyId, float quantity) {
//        log.debug("Try to place LIMIT SELL order: {} for {}.", symbol, quantity);
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            placeLimitSellOrder(symbol, strategyId, quantity,
                    orderBookResponse.getAsks().get(0).getPrice());
        });
    }

    public void placeLimitBuyOrder(String symbol, int strategyId, float quantity, String price) {
        log.debug("Sending async request to place new limit order to buy {} {} at {}.", quantity, symbol, price);
        asyncRestClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC, Float.toString(quantity), price, strategyId), limitBuyResponse -> {
            marketInfo.pairOrderPlaced(symbol, strategyId, quantity, OrderSide.BUY);
            log.debug("Async LIMIT BUY order placed: {}", limitBuyResponse);
        });
    }

    public void placeLimitSellOrder(String symbol, int strategyId, float quantity, String price) {
        asyncRestClient.newOrder(NewOrder.limitSell(symbol, TimeInForce.GTC, Float.toString(quantity), price, strategyId), limitSellResponse -> {
            marketInfo.pairOrderPlaced(symbol, strategyId, quantity, OrderSide.SELL);
            log.debug("Async LIMIT SELL order placed: {}", limitSellResponse);
        });
    }

    public void placeMarketBuyOrder(String symbol, float quantity, int strategyId) {
        asyncRestClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity)), strategyId),
                new BinanceApiCallback<NewOrderResponse>() {
                    @Override
                    public void onResponse(NewOrderResponse marketBuyOrderCallback) {
                        log.debug("Async MARKET BUY order placed: {}", marketBuyOrderCallback);
                    }

                    @Override
                    public void onFailure(Throwable cause) {
                        if (cause instanceof BinanceApiException exception) {
                            log.info("Error with code {} while trying to place buy order of {}. Remove from placed orders.", exception.getError().getCode(), symbol);
                            if (exception.getError().getCode() == -2010) {
                                marketInfo.pairOrderFilled(symbol, strategyId);
                            }
                        }
                    }
                });
    }

//    public void placeMarketBuyOrder(String symbol, float quantity, int strategyId) {
//        asyncRestClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity)), strategyId),
//                marketBuyOrderCallback -> {
//                    log.debug("Async MARKET BUY order placed: {}", marketBuyOrderCallback);
//                });
//    }

    public void placeMarketSellOrder(String symbol, float quantity, int strategyId) {
        asyncRestClient.newOrder(NewOrder.marketSell(symbol, String.valueOf(quantity), strategyId),
                marketSellOrderCallback -> {
                    log.debug("Async MARKET SELL order placed: {}", marketSellOrderCallback);
                });
    }

    public List<AssetBalance> recieveOpenedLongPositionsFromMarket() {
        return accountInfo.getAllAccountBalances().stream()
                .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB")))
                .collect(Collectors.toList());
    }

    public void closePostitions(Map<String, Float> positionsToClose) {
        log.debug("Start to go out at last price from {} positions to close:\n{}", positionsToClose.size(), positionsToClose);
        positionsToClose.forEach((key, value) -> placeMarketSellOrder(key, value, 0));
    }
}

