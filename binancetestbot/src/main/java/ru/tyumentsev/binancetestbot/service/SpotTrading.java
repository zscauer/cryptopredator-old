package ru.tyumentsev.binancetestbot.service;

import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class SpotTrading {
    
    @Autowired
    BinanceApiRestClient restClient;

    public NewOrderResponse placeMarketOrder(String symbol, Double quantity) {
        log.info("Placing market order to buy " + symbol + " " + quantity);
        return restClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity))));
    }

    public NewOrderResponse placeLimitOrder(String symbol, String quantity, String price) {
        return restClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC, quantity, price));
    }

    public void closeAllPostitions(Map<String, Double> positionsToClose) {
        for (Entry<String, Double> entrySet : positionsToClose.entrySet()) {
            log.info("Placing market order to sell " + entrySet.getKey() + " " + entrySet.getValue());
            restClient.newOrder(NewOrder.marketSell(entrySet.getKey(), String.valueOf(entrySet.getValue())));
        }
    }

}
