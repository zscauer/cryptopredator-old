package ru.tyumentsev.binancespotbot.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.domain.market.TickerStatistics;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketInfo {

    BinanceApiRestClient restClient;

    public List<String> getAvailableTradePairs(final String quoteAsset) {
        return restClient.getExchangeInfo().getSymbols().stream()
                .filter(symbolInfo -> symbolInfo.getStatus() == SymbolStatus.TRADING
                        && symbolInfo.getQuoteAsset().equalsIgnoreCase(quoteAsset)
                        && symbolInfo.isSpotTradingAllowed())
                .map(SymbolInfo::getSymbol)
                .collect(Collectors.toList());
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

    public List<Candlestick> getCandleSticks(String symbol, CandlestickInterval interval, Integer limit) {
        return restClient.getCandlestickBars(symbol, interval, limit);
    }
}
