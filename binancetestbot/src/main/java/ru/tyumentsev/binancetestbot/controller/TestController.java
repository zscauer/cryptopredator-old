package ru.tyumentsev.binancetestbot.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.AccountUpdateEvent;
import com.binance.api.client.domain.event.BookTickerEvent;
import com.binance.api.client.domain.event.DepthEvent;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/test")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TestController {

    BinanceApiClientFactory factory;
    BinanceApiRestClient restClient;
    BinanceApiWebSocketClient webSocketClient;

    public TestController(BinanceApiClientFactory factory) {
        this.factory = factory;
        this.restClient = factory.newRestClient();
        this.webSocketClient = factory.newWebSocketClient();
    }

    @GetMapping("/test1")
    public List<AssetBalance> testMethod1() {
        Account account = restClient.getAccount();
        return account.getBalances();
    }

    @GetMapping("/test2")
    public void testMethod2() {
        String listenKey = restClient.startUserDataStream();
        
        webSocketClient.onUserDataUpdateEvent(listenKey, response -> {
            if (response.getEventType() == UserDataUpdateEventType.ACCOUNT_POSITION_UPDATE) {
                AccountUpdateEvent accountUpdateEvent = response.getOutboundAccountPositionUpdateEvent();
                // Print new balances of every available asset
                System.out.println(accountUpdateEvent.getBalances());
            } else {
                OrderTradeUpdateEvent orderTradeUpdateEvent = response.getOrderTradeUpdateEvent();
                // Print details about an order/trade
                System.out.println(orderTradeUpdateEvent);

                // Print original quantity
                System.out.println(orderTradeUpdateEvent.getOriginalQuantity());

                // Or price
                System.out.println(orderTradeUpdateEvent.getPrice());
            }
        });
        System.out.println("Waiting for events...");
    }

    @GetMapping("/test3")
    public void testMethod3() {
        // String listenKey = restClient.startUserDataStream();
        
       OrderBook orderBook = restClient.getOrderBook("BTCUSDT", 20);
       List<OrderBookEntry> asks = orderBook.getAsks();
       System.out.println(asks);
    }

    @GetMapping("/test4")
    public void testMethod4() {
        // String listenKey = restClient.startUserDataStream();
        
        webSocketClient.onBookTickerEvent("BTCUSDT", (BookTickerEvent resp) -> {
            System.out.println(resp.getAskPrice() + resp.getBidPrice());
       });

    }
}
