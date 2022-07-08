package ru.tyumentsev.binancetestbot.controller;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.BookTickerEvent;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.BookTicker;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.binance.api.client.domain.market.TickerPrice;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/test")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TestController {

    // final BinanceApiClientFactory factory;
    final BinanceApiRestClient restClient;
    final BinanceApiWebSocketClient webSocketClient;

    Closeable openedWebSocket;

    public TestController(BinanceApiClientFactory factory) {
        // this.factory = factory;
        this.restClient = factory.newRestClient();
        this.webSocketClient = factory.newWebSocketClient();
    }

    @GetMapping("/closeWS")
    public void closeWebSocket() {
        try {
            if (openedWebSocket == null) {
                System.out.println("WebSocket is null");
            } else {
                System.out.println("Closing web socket " + openedWebSocket.toString());
                openedWebSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/accountBalance")
    public List<AssetBalance> accountBalance() {
        Account account = restClient.getAccount();
        return account.getBalances();
    }

    @GetMapping("/accountBalance/{ticker}")
    public AssetBalance assetBalance(@PathVariable String ticker) {
        Account account = restClient.getAccount();
        return account.getAssetBalance(ticker.toUpperCase());
    }

    @GetMapping("/orderBook/{pair}")
    public OrderBook orderBook(@PathVariable String pair) { // get orders
        return restClient.getOrderBook(pair.toUpperCase(), 10);
    }
    
    @GetMapping("/orderBook/{pair}/asks")
    public OrderBookEntry orderBookAsks(@PathVariable String pair) { // get orders
        OrderBook orderBook = restClient.getOrderBook(pair.toUpperCase(), 10);
        List<OrderBookEntry> asks = orderBook.getAsks();
        OrderBookEntry firstAskEntry = asks.get(0);
        System.out.println(firstAskEntry.getPrice() + " / " + firstAskEntry.getQty());

        return firstAskEntry;
    }

    @GetMapping("/orderBook/{pair}/bids")
    public OrderBookEntry orderBookBids(@PathVariable String pair) { // get orders
        OrderBook orderBook = restClient.getOrderBook(pair.toUpperCase(), 10);
        List<OrderBookEntry> bids = orderBook.getBids();
        OrderBookEntry firstBidEntry = bids.get(0);
        System.out.println(firstBidEntry.getPrice() + " / " + firstBidEntry.getQty());

        return firstBidEntry;
    }

    @GetMapping("/bookTickerEvent/{pair}")
    public void printBookTickerEvents(@PathVariable String pair) {
        closeWebSocket();
        
        openedWebSocket = webSocketClient.onBookTickerEvent(pair.toLowerCase(), (BookTickerEvent response) -> {
            System.out.println("Ask price " + response.getAskPrice() + " / " + "Bid price " + response.getBidPrice());
        });
    }

    @GetMapping("/candlesTickEvent/{pair}")
    public void printCandleStickEvents(@PathVariable String pair) {
        closeWebSocket();
        
        openedWebSocket = webSocketClient.onCandlestickEvent(pair.toLowerCase(), CandlestickInterval.ONE_MINUTE,
                new BinanceApiCallback<CandlestickEvent>() {
                    @Override
                    public void onResponse(CandlestickEvent response) {
                        System.out.println(response);
                    }

                    @Override
                    public void onFailure(Throwable cause) {
                        System.err.println("web socket failure");
                        cause.printStackTrace(System.err);
                    }
                });
    }

    @GetMapping("/prices")
    public List<TickerPrice> getAllPrices() {
        return restClient.getAllPrices();
    }

    @GetMapping("/bookTickers")
    public List<BookTicker> getBookTickers() {
        return restClient.getBookTickers();
        
    }
    

}
