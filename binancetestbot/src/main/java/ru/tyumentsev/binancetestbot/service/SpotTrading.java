package ru.tyumentsev.binancetestbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;

@Service
public class SpotTrading {
    
    @Autowired
    BinanceApiRestClient restClient;
    // @Autowired
    // AccountManager accountManager;

    public NewOrderResponse placeMarketOrder(String symbol, Double quantity) {
        return restClient.newOrder(NewOrder.marketBuy(symbol, String.valueOf(Math.ceil(quantity))));
    }

    public NewOrderResponse placeLimitOrder(String symbol, String quantity, String price) {
        return restClient.newOrder(NewOrder.limitBuy(symbol, TimeInForce.GTC, quantity, price));
    }

}
