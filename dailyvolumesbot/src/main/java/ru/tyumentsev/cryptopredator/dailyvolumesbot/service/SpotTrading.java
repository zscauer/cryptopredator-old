package ru.tyumentsev.cryptopredator.dailyvolumesbot.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.binance.api.client.domain.account.AssetBalance;
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

    public void placeBuyOrderFast(final String symbol, final double price, String quoteAsset) {
        if (Thread.holdsLock(this)) {
            log.info("placeBuyOrderFast({}) object monitor already locked by the current thread {} ({}).", symbol, Thread.currentThread().getName(), Thread.currentThread().getId());
            return;
        }
        marketInfo.pairOrderPlaced(symbol);
        synchronized (this) {
            int availableOrdersCount = accountManager.getFreeAssetBalance(quoteAsset).intValue() / minimalAssetBalance;
            if (availableOrdersCount > 1) {
//            marketInfo.pairOrderPlaced(symbol);
//            placeLimitBuyOrderAtLastMarketPrice(symbol, baseOrderVolume / price);
                placeMarketBuyOrder(symbol, baseOrderVolume / price);
            } else {
                marketInfo.pairOrderFilled(symbol);
                log.debug("NOT enough balance to buy {}.", symbol);
            }
        }
    }

    public void placeSellOrderFast(final String symbol, final Double qty) {
        if (Thread.holdsLock(this)) {
            log.info("placeSellOrderFast({}) object monitor already locked by the current thread {} ({}).", symbol, Thread.currentThread().getName(), Thread.currentThread().getId());
            return;
        }
        marketInfo.pairOrderPlaced(symbol);
        synchronized (this) {
            placeMarketSellOrder(symbol, qty);
        }
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

    public void placeMarketBuyOrder(String symbol, double quantity) {
        asyncRestClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity))),
            marketBuyOrderCallback -> {
                log.debug("Async MARKET BUY order placed: {}", marketBuyOrderCallback);
            });
    }

    public void placeMarketSellOrder(String symbol, double quantity) {
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
