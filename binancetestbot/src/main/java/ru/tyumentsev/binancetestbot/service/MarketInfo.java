package ru.tyumentsev.binancetestbot.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
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
}
