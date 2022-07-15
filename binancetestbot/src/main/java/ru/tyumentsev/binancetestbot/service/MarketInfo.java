package ru.tyumentsev.binancetestbot.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.domain.market.TickerStatistics;

@Service
public class MarketInfo {

    @Autowired
    BinanceApiRestClient restClient;
    @Autowired
    BinanceApiWebSocketClient webSocketClient;

    public List<String> getAvailableTradePairs(final String ticker) {
        ExchangeInfo info = restClient.getExchangeInfo();
        List<SymbolInfo> symbolsInfo = info.getSymbols();

        List<String> requestedPairs = symbolsInfo.stream()
                .filter(symbolInfo -> symbolInfo.getStatus() == SymbolStatus.TRADING
                        && symbolInfo.getQuoteAsset().equalsIgnoreCase(ticker)
                        && symbolInfo.isSpotTradingAllowed()
                        && symbolInfo.trailingStopIsAllowed())
                .map(symbolInfo -> symbolInfo.getSymbol())
                .collect(Collectors.toList());

        return requestedPairs;
    }

    public TickerStatistics getWindowPriceChange(String symbol, String windowSize) {
        return restClient.getWindowPriceChangeStatistics(symbol, windowSize);
    }

    public List<TickerStatistics> getAllWindowPriceChange(String symbols, String windowSize) {
        return restClient.getAllWindowPriceChangeStatistics(symbols, windowSize);
    }

    public TickerPrice getLastTickerPrice(String symbol) {
        return restClient.getPrice(symbol);
    }

    public List<TickerPrice> getLastTickersPrices(String symbols) {
        return restClient.getPrices(symbols);
    }

    public Set<Candlestick> getCandleSticks(List<String> symbols, CandlestickInterval interval, Integer limit) {
        Set<Candlestick> filteredSticks = new HashSet<>();
        
        // for (String symbol : symbols) {
        //     // List<Candlestick> sticks = restClient.getCandlestickBars(symbol.toUpperCase(), interval, limit);
        //     // Candlestick stick = sticks.get(0);
        //     // if (Double.parseDouble(stick.getClose()) < 1) {
        //         filteredSticks.add(restClient.getCandlestickBars(symbol.toUpperCase(), interval, limit).get(0));
        //     // }  
        // }

        symbols.stream().forEach(symbol -> {
            filteredSticks.add(restClient.getCandlestickBars(symbol, interval, limit).get(0));
        });

        // symbols.stream().forEach(symbol -> {
        //     List<Candlestick> sticks = restClient.getCandlestickBars(symbol.toUpperCase(), interval, limit);
        //     Candlestick stick = sticks.get(0);
        //     if (Double.parseDouble(stick.getClose()) < 1) {
        //         filteredSticks.add(sticks.get(limit - 1));
        //     }
        // });

       return filteredSticks;
    }
}
