package ru.tyumentsev.binancespotbot.service;

import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;

import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancespotbot.cache.MarketData;

@Service
@Log4j2
public class SpotTrading {

    @Autowired
    BinanceApiAsyncRestClient asyncRestClient;
    @Autowired
    MarketData marketData;

    public void placeLimitBuyOrderAtLastMarketPrice(String symbol, Double quantity) {
        log.debug("Try to place limit buy order: {} for {}.", symbol, quantity);
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            placeLimitBuyOrder(symbol, String.valueOf(Math.ceil(quantity)),
                    orderBookResponse.getBids().get(0).getPrice());
        });
    }

    public void placeLimitSellOrderAtLastMarketPrice(String symbol, Double quantity) {
        log.debug("Try to place limit sell order: {} for {}.", symbol, quantity);
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            placeLimitSellOrder(symbol, String.valueOf(quantity),
                    orderBookResponse.getAsks().get(0).getPrice());
        });
    }

    public void placeLimitBuyOrder(String symbol, String quantity, String price) {
        log.info("Sending async request to place new limit order to buy {} {} at {}.", quantity, symbol, price);
        asyncRestClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC, quantity, price), limitBuyResponse -> {
            log.info("Async limit buy order placed: {}", limitBuyResponse);
        });
    }

    public void placeLimitSellOrder(String symbol, String quantity, String price) {
        asyncRestClient.newOrder(NewOrder.limitSell(symbol, TimeInForce.GTC, quantity, price), limitSellResponse -> {
            log.info("Async limit sell order placed: {}", limitSellResponse);
        });
    }

    public void placeMarketBuyOrder(String symbol, Double quantity) {
        asyncRestClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity))),
                marketBuyOrderCallback -> {
                    log.info("Async market buy order placed: {}",
                            marketBuyOrderCallback);
                });
    }

    public void placeMarketSellOrder(String symbol, Double quantity) {
        asyncRestClient.newOrder(NewOrder.marketSell(symbol, String.valueOf(quantity)),
                marketSellOrderCallback -> {
                    log.info("Async market sell order placed: {}",
                            marketSellOrderCallback);
                });
    }

    public void closeAllPostitions(Map<String, Double> positionsToClose) {
        log.debug("Start to go out from {} positions to close:\n{}", positionsToClose.size(), positionsToClose);
        for (Entry<String, Double> entrySet : positionsToClose.entrySet()) {
            placeLimitSellOrderAtLastMarketPrice(entrySet.getKey(), entrySet.getValue());
        }
    }
}
