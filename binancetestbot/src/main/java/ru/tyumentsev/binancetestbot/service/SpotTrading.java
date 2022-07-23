package ru.tyumentsev.binancetestbot.service;

import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;

import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancetestbot.cache.MarketData;

@Service
@Log4j2
public class SpotTrading {

    @Autowired
    BinanceApiRestClient restClient;
    @Autowired
    BinanceApiAsyncRestClient asyncRestClient;
    @Autowired
    MarketData marketData;

    public NewOrderResponse placeMarketBuyOrder(String symbol, Double quantity) {
        log.info("Placing market order to buy " + symbol + " " + Math.ceil(quantity));
        return restClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity))));
    }

    public void placeLimitBuyOrder(String symbol, String quantity, String price) {
        // return restClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC,
        // quantity, price));
        asyncRestClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC, quantity, price), response -> {
            marketData.putOpenedPositionToPriceMonitoring(response.getSymbol(),
                    Double.parseDouble(response.getPrice()));
        });
    }

    public void placeLimitBuyOrderAtLastMarketPrice(String symbol, Double quantity) {
        String price = restClient.getOrderBook(symbol, 1).getBids().get(0).getPrice();
        log.info("Placing market order to buy at last order book price " + symbol + " " + Math.ceil(quantity));
        asyncRestClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC, String.valueOf(Math.ceil(quantity)), price),
                response -> {
                    log.info("Async buy order complete, put result in opened positions cache: " + response.getSymbol()
                            + " " + response.getPrice());
                    marketData.putOpenedPositionToPriceMonitoring(response.getSymbol(),
                            Double.parseDouble(response.getPrice()));
                });

        // return restClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC,
        // quantity, price));
    }

    public void placeLimitSellOrderAtLastMarketPrice(String symbol, Double quantity) {
        String price = restClient.getOrderBook(symbol, 1).getAsks().get(0).getPrice();
        log.info("Placing market order to sell at last order book price " + symbol + " " + quantity);
        asyncRestClient.newOrder(
                NewOrder.limitSell(symbol, TimeInForce.GTC, String.valueOf(quantity), price),
                response -> {
                    log.info("Async sell order complete, remove result from opened positions cache: "
                            + response.getSymbol()
                            + " " + response.getPrice());

                    marketData.removeClodesPositoinsFromPriceMonitoring(response.getSymbol());
                });

        // return restClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC,
        // quantity, price));
    }

    public void closeAllPostitions(Map<String, Double> positionsToClose) {
        for (Entry<String, Double> entrySet : positionsToClose.entrySet()) {
            placeLimitSellOrderAtLastMarketPrice(entrySet.getKey(), entrySet.getValue());
            // log.info("Placing market order to sell " + entrySet.getKey() + " " +
            // entrySet.getValue());
            // restClient.newOrder(NewOrder.marketSell(entrySet.getKey(),
            // String.valueOf(entrySet.getValue())));
        }
    }

}
