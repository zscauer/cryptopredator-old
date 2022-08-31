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

    public void placeMarketBuyOrder(String symbol, Double quantity) {
        // log.info("Placing market order to buy {} at {}", symbol, Math.ceil(quantity));
        asyncRestClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity))),
                marketBuyOrderCallback -> {
                    log.info("Async market buy order placed, put result in opened positions cache: {}",
                            marketBuyOrderCallback);
                    marketData.putOpenedPositionToPriceMonitoring(marketBuyOrderCallback.getSymbol(),
                            Double.parseDouble(marketBuyOrderCallback.getPrice()));
                });
    }

    public void placeMarketSellOrder(String symbol, Double quantity) {
        // log.info("Placing market order to sell {} at {}", symbol, Math.ceil(quantity));
        asyncRestClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity))),
                marketBuyOrderCallback -> {
                    log.info("Async market sell order placed, remove result from opened positions cache {}",
                            marketBuyOrderCallback);
                    marketData.removeClosedPositionFromPriceMonitoring(marketBuyOrderCallback.getSymbol());
                });
    }

    public void placeLimitBuyOrder(String symbol, String quantity, String price) {
        asyncRestClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC, quantity, price), limitBuyResponse -> {
            // log.info("Async limit buy order placed: {}", limitBuyResponse);
        });
    }

    public void placeLimitBuyOrderAtLastMarketPrice(String symbol, Double quantity) {
        // log.info("Placing limit order to buy at last order book price {} at {}", symbol, Math.ceil(quantity));
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            placeLimitBuyOrder(symbol, String.valueOf(Math.ceil(quantity)),
                    orderBookResponse.getBids().get(0).getPrice());
        });
    }

    public void placeLimitSellOrderAtLastMarketPrice(String symbol, Double quantity) {
        // log.info("Placing limit order to sell at last order book price " + symbol + " " + quantity);
        asyncRestClient.getOrderBook(symbol, 1, orderBookResponse -> {
            asyncRestClient.newOrder(
                    NewOrder.limitSell(symbol, TimeInForce.GTC, String.valueOf(quantity),
                            orderBookResponse.getAsks().get(0).getPrice()),
                    limitSellResponse -> {
                        log.info("Async limit sell order placed: {}",
                                limitSellResponse);
                    });
        });
    }

    public void closeAllPostitions(Map<String, Double> positionsToClose) {
        for (Entry<String, Double> entrySet : positionsToClose.entrySet()) {
            placeLimitSellOrderAtLastMarketPrice(entrySet.getKey(), entrySet.getValue());
        }
    }

}
