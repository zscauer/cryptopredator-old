package ru.tyumentsev.binancespotbot.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.binance.api.client.domain.market.CandlestickInterval;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class SpotTrading {

    final BinanceApiAsyncRestClient asyncRestClient;
    final MarketInfo marketInfo;

    @Value("${strategy.global.minimalAssetBalance}")
    int minimalAssetBalance;
    @Value("${strategy.global.baseOrderVolume}")
    int baseOrderVolume;

    public void buyAssets(Map<String, Double> pairsToBuy, final String quoteAsset, final AccountManager accountManager) {
        if (pairsToBuy.size() > 0) {
            int availableOrdersCount = accountManager.getFreeAssetBalance(quoteAsset).intValue() / minimalAssetBalance;
            Map<String, Double> remainsPairs = new HashMap<>(); // collect pairs which can't be bought.

            for (Entry<String, Double> pairToBuy : pairsToBuy.entrySet()) {
                if (availableOrdersCount > 0
                        && marketInfo.pairHadTradesInThePast(pairToBuy.getKey(), CandlestickInterval.DAILY, 3)) {
                    placeLimitBuyOrderAtLastMarketPrice(pairToBuy.getKey(),
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
        }

        pairsToBuy.clear();
    }

    public void placeBuyOrderFast(String symbol, Double price, String quoteAsset, AccountManager accountManager) {
        int availableOrdersCount = accountManager.getFreeAssetBalance(quoteAsset).intValue() / minimalAssetBalance;
        if (availableOrdersCount > 1) {
            marketInfo.pairOrderPlaced(symbol);
            placeLimitBuyOrderAtLastMarketPrice(symbol, baseOrderVolume / price);
        } else {
            log.info("NOT enough balance to buy {}.", symbol);
        }
    }

    public void placeSellOrderFast(String symbol, Double qty) {
        marketInfo.pairOrderPlaced(symbol);
        placeMarketSellOrder(symbol, qty);
    }

    public void placeLimitBuyOrderAtLastMarketPrice(String symbol, Double quantity) {
//        log.debug("Try to place LIMIT BUY order: {} for {}.", symbol, quantity);
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            placeLimitBuyOrder(symbol, String.valueOf(Math.ceil(quantity)),
                    orderBookResponse.getBids().get(0).getPrice());
        });
    }

    public void placeLimitSellOrderAtLastMarketPrice(String symbol, Double quantity) {
//        log.debug("Try to place LIMIT SELL order: {} for {}.", symbol, quantity);
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            placeLimitSellOrder(symbol, String.valueOf(quantity),
                    orderBookResponse.getAsks().get(0).getPrice());
        });
    }

    public void placeLimitBuyOrder(String symbol, String quantity, String price) {
        log.debug("Sending async request to place new limit order to buy {} {} at {}.", quantity, symbol, price);
        asyncRestClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC, quantity, price), limitBuyResponse -> {
            marketInfo.pairOrderPlaced(symbol);
            log.debug("Async LIMIT BUY order placed: {}", limitBuyResponse);
        });
    }

    public void placeLimitSellOrder(String symbol, String quantity, String price) {
        asyncRestClient.newOrder(NewOrder.limitSell(symbol, TimeInForce.GTC, quantity, price), limitSellResponse -> {
            marketInfo.pairOrderPlaced(symbol);
            log.debug("Async LIMIT SELL order placed: {}", limitSellResponse);
        });
    }

    public void placeMarketBuyOrder(String symbol, Double quantity) {
        asyncRestClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity))),
            marketBuyOrderCallback -> {
                log.debug("Async MARKET BUY order placed: {}", marketBuyOrderCallback);
            });
    }

    public void placeMarketSellOrder(String symbol, Double quantity) {
        asyncRestClient.newOrder(NewOrder.marketSell(symbol, String.valueOf(quantity)),
            marketSellOrderCallback -> {
                log.debug("Async MARKET SELL order placed: {}", marketSellOrderCallback);
            });
    }

    public void closePostitions(Map<String, Double> positionsToClose) {
        log.debug("Start to go out at last price from {} positions to close:\n{}", positionsToClose.size(), positionsToClose);
        positionsToClose.forEach(this::placeLimitSellOrderAtLastMarketPrice);
    }
}
