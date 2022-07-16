package ru.tyumentsev.binancetestbot.controller;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.api.client.BinanceApiCallback;
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
import com.binance.api.client.domain.market.TickerStatistics;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.service.MarketInfo;
import ru.tyumentsev.binancetestbot.strategy.BuyFastGrowth;

@RestController
@RequestMapping("/test")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class TestController {

    final BinanceApiRestClient restClient;
    final BinanceApiWebSocketClient webSocketClient;
    final MarketInfo marketInfo;
    final MarketData marketData;
    final BuyFastGrowth buyFastGrowth;

    Closeable openedWebSocket;

    @GetMapping(value = "/closeWS")
    public Map<String, String> closeWebSocket() {
        try {
            if (openedWebSocket == null) {
                return Collections.singletonMap("response", "WebSocket is null");
            } else {
                openedWebSocket.close();
                return Collections.singletonMap("response", "Closing web socket " + openedWebSocket.toString());
            }
        } catch (IOException e) {
            return Collections.singletonMap("response", e.getStackTrace().toString());
        }
    }

    @GetMapping("/availablePairs/{ticker}")
    public List<String> getAvailableTradePairs(@PathVariable final String ticker) {
        return marketInfo.getAvailableTradePairs(ticker);
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

    @GetMapping("/windowPriceChange")
    public TickerStatistics getWindowPriceChange(@RequestParam("symbol") String symbol,
            @RequestParam("windowSize") String windowSize) {
        return marketInfo.getWindowPriceChange(symbol, windowSize);
    }

    @GetMapping("/windowPriceChange/list")
    public List<TickerStatistics> getAllWindowPriceChange(@RequestParam("symbols") String symbols,
            @RequestParam("windowSize") String windowSize) {
        return marketInfo.getAllWindowPriceChange(symbols, windowSize);
    }

    @GetMapping("/buyFastGrowth")
    public List<TickerStatistics> buyFastGrowth(@RequestParam("asset") String asset) {
        return buyFastGrowth.addPairsToBuy(asset);
    }

    @GetMapping("/buyFastGrowth/openedPositions")
    public Map<String, Double> getOpenedPositions() {
        return marketData.getOpenedPositions();
    }

    // @GetMapping("/buyBigVolumeChange/findCheapPairs")
    // public List<String> findCheapPairs() {
    // return buyBigVolumeGrowth.findCheapPairs();
    // }

    @GetMapping("/buyBigVolumeChange/getMonitoredCandleSticks")
    public List<CandlestickEvent> getMonitoredCandleSticks(@RequestParam("asset") String asset) {
        return marketData.getCachedCandleSticks().get(asset);
    }

}
